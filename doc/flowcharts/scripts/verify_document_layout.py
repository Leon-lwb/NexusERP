#!/usr/bin/env python3
"""Render a private DOCX copy with Windows Word/WPS and inspect its PDF.

python -X utf8 verify_document_layout.py --input C:\\manual.docx \
    --output-dir C:\\artifacts --update-copy

The input is never opened in Office. Only --update-copy saves refreshed fields
to the artifact copy. No running Office process is attached to or terminated.
Exit codes: 0 = rendered with no detected geometry issues, 1 = render failure,
2 = detected layout issues. Raster previews still require visual review.
"""

import argparse
import gc
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import sys
import tempfile
import time
from zipfile import ZipFile
import xml.etree.ElementTree as ET


class VerificationError(RuntimeError):
    pass


def sha256_file(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def docx_metadata(path):
    ns = {
        "w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
        "a": "http://schemas.openxmlformats.org/drawingml/2006/main",
    }
    with ZipFile(path) as archive:
        names = archive.namelist()
        if any(name.lower().endswith("vbaproject.bin") for name in names):
            raise VerificationError("Macro-containing documents are not supported")
        root = ET.fromstring(archive.read("word/document.xml"))
        instructions = [node.text or "" for node in root.findall(".//w:instrText", ns)]
        instructions += [
            node.get("{" + ns["w"] + "}instr", "")
            for node in root.findall(".//w:fldSimple", ns)
        ]
        return {
            "embedded_media_count": sum(name.startswith("word/media/") for name in names),
            "body_picture_count": len(root.findall(".//a:blip", ns)),
            "toc_instructions": [value for value in instructions if value.lstrip().startswith("TOC ")],
        }


def prepare_copy(input_path, output_dir):
    source = Path(input_path)
    output = Path(output_dir)
    if not source.is_absolute() or not output.is_absolute():
        raise VerificationError("--input and --output-dir must be absolute paths")
    source = source.resolve(strict=True)
    if not source.is_file() or source.suffix.lower() != ".docx":
        raise VerificationError("--input must be an existing DOCX file")
    output.mkdir(parents=True, exist_ok=True)
    run_dir = Path(tempfile.mkdtemp(prefix="layout-", dir=output.resolve()))
    working_copy = run_dir / "document.layout-copy.docx"
    shutil.copy2(source, working_copy)
    if sha256_file(source) != sha256_file(working_copy):
        raise VerificationError("The input changed while it was being copied")
    return {
        "input": source,
        "run_dir": run_dir,
        "working_copy": working_copy,
        "pdf": run_dir / "document.pdf",
        "report": run_dir / "layout-report.json",
        "contact_sheet": run_dir / "contact-sheet.png",
    }


def out_of_bounds(box, page_box, tolerance=1.0):
    values = tuple(box) + tuple(page_box)
    if len(values) != 8 or not all(math.isfinite(value) for value in values):
        return True
    x0, y0, x1, y1 = box
    px0, py0, px1, py1 = page_box
    return (
        x0 > x1 or y0 > y1 or x0 < px0 - tolerance or y0 < py0 - tolerance
        or x1 > px1 + tolerance or y1 > py1 + tolerance
    )


def select_sample_pages(pages, limit=8):
    if not pages or limit < 1:
        return []
    limit = min(limit, len(pages))
    chosen = set([pages[0]["page"], pages[-1]["page"]][:limit])
    if len(chosen) < limit and len(pages) > 1:
        chosen.add(pages[1]["page"])
    image_pages = [page["page"] for page in pages if page["image_count"] and page["page"] not in chosen]
    slots = limit - len(chosen)
    if len(image_pages) <= slots:
        chosen.update(image_pages)
    elif slots:
        for index in range(slots):
            position = round(index * (len(image_pages) - 1) / max(1, slots - 1))
            chosen.add(image_pages[position])
    while len(chosen) < limit:
        remaining = [page["page"] for page in pages if page["page"] not in chosen]
        chosen.add(max(remaining, key=lambda page: min(abs(page - other) for other in chosen)))
    return sorted(chosen)


def require_empty_new_instance(document_count, new_process_ids):
    if document_count != 0 or not new_process_ids:
        raise VerificationError(
            "Cannot confirm a new, empty Office instance; refusing to change or quit it"
        )


def office_processes():
    import pywintypes
    import win32api
    import win32process

    result = {}
    for pid in win32process.EnumProcesses():
        try:
            handle = win32api.OpenProcess(1040, False, pid)
            try:
                executable = win32process.GetModuleFileNameEx(handle, 0)
                if Path(executable).name.lower() in ("wps.exe", "winword.exe"):
                    result[pid] = executable
            finally:
                handle.Close()
        except (OSError, pywintypes.error):
            continue
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--input", required=True, help="Absolute path to the original DOCX")
    parser.add_argument("--output-dir", required=True, help="Absolute artifact directory; each run gets a new subdirectory")
    parser.add_argument("--update-copy", action="store_true", help="Save refreshed fields/TOC into the artifact DOCX, never the input")
    parser.add_argument("--sample-count", type=int, default=8, help="Number of representative PDF previews (default: 8)")
    parser.add_argument("--dpi", type=int, default=144, help="Preview DPI (default: 144)")
    args = parser.parse_args(argv)
    paths = None
    report = {"status": "failed", "input": args.input, "update_copy_requested": args.update_copy}
    exit_code = 1
    try:
        if args.sample_count < 1 or not 72 <= args.dpi <= 300:
            raise VerificationError("--sample-count must be positive and --dpi must be between 72 and 300")
        paths = prepare_copy(args.input, args.output_dir)
        report["artifacts"] = {key: str(value) for key, value in paths.items()}
        report["input_sha256_before"] = sha256_file(paths["input"])
        report["source_docx"] = docx_metadata(paths["working_copy"])
        render_copy(paths, args.update_copy, report)
        report["pdf"] = inspect_pdf(paths, args.sample_count, args.dpi)
        if report["pdf"]["page_count"] != report["office"]["page_count_com"]:
            raise VerificationError("PDF and Office pagination disagree")
        report["copy_sha256_after"] = sha256_file(paths["working_copy"])
        report["copy_docx"] = docx_metadata(paths["working_copy"])
        if report["copy_docx"]["body_picture_count"] != report["source_docx"]["body_picture_count"]:
            raise VerificationError("Saving the artifact copy changed its body picture count")
        report["status"] = "ok" if report["pdf"]["geometry_check_passed"] else "layout_issues"
        exit_code = 0 if report["status"] == "ok" else 2
    except Exception as exc:
        report["status"] = "failed"
        report["error"] = {"type": type(exc).__name__, "message": str(exc)}
        exit_code = 1
    finally:
        if paths:
            try:
                report["input_sha256_after"] = sha256_file(paths["input"])
                report["input_unchanged"] = report.get("input_sha256_before") == report["input_sha256_after"]
                if not report["input_unchanged"]:
                    report["status"] = "failed"
                    report["input_change_error"] = "The input changed during verification; it was never opened by this script"
                    exit_code = 1
                paths["report"].write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
            except Exception as exc:
                report["status"] = "failed"
                report["report_write_error"] = str(exc)
                exit_code = 1
    summary = {
        "status": report["status"], "report": str(paths["report"]) if paths else None,
        "working_copy": str(paths["working_copy"]) if paths else None,
        "pdf": str(paths["pdf"]) if paths and paths["pdf"].is_file() else None,
        "page_count": report.get("pdf", {}).get("page_count"),
        "image_pages": report.get("pdf", {}).get("image_pages"),
        "independent_session": report.get("office", {}).get("isolation", {}).get("independent_confirmed", False),
        "input_unchanged": report.get("input_unchanged"),
        "error": report.get("error") or report.get("input_change_error") or report.get("report_write_error"),
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return exit_code


def installed_renderers():
    import ctypes
    from ctypes import wintypes
    import winreg

    parse_command = ctypes.WinDLL("shell32").CommandLineToArgvW
    parse_command.argtypes = [wintypes.LPCWSTR, ctypes.POINTER(ctypes.c_int)]
    parse_command.restype = ctypes.POINTER(wintypes.LPWSTR)
    free_memory = ctypes.WinDLL("kernel32").LocalFree
    free_memory.argtypes = [ctypes.c_void_p]
    free_memory.restype = ctypes.c_void_p
    servers = []
    seen = set()
    for progid in ("Word.Application", "KWPS.Application"):
        for view in (winreg.KEY_WOW64_64KEY, winreg.KEY_WOW64_32KEY):
            try:
                with winreg.OpenKey(winreg.HKEY_CLASSES_ROOT, progid + "\\CLSID", 0, winreg.KEY_READ | view) as key:
                    clsid = winreg.QueryValueEx(key, None)[0]
                with winreg.OpenKey(winreg.HKEY_CLASSES_ROOT, "CLSID\\" + clsid + "\\LocalServer32", 0, winreg.KEY_READ | view) as key:
                    command = os.path.expandvars(winreg.QueryValueEx(key, None)[0])
                count = ctypes.c_int()
                arguments = parse_command(command, ctypes.byref(count))
                if not arguments:
                    continue
                try:
                    executable = Path(arguments[0])
                finally:
                    free_memory(ctypes.cast(arguments, ctypes.c_void_p))
                if clsid in seen or not executable.is_file():
                    continue
                if executable.name.lower() not in ("winword.exe", "wps.exe"):
                    continue
                seen.add(clsid)
                servers.append({
                    "progid": progid, "clsid": clsid, "executable": str(executable),
                    "registry_view_bits": 32 if view == winreg.KEY_WOW64_32KEY else 64,
                    "engine": "WPS" if executable.name.lower() == "wps.exe" else "Word",
                })
            except OSError:
                continue
    return sorted(servers, key=lambda server: (server["engine"] != "Word", server["progid"] != "KWPS.Application"))


def update_story_fields(document, results):
    for initial in document.StoryRanges:
        story = initial
        visited = 0
        while story is not None:
            count = int(story.Fields.Count)
            if count:
                outcome = story.Fields.Update()
                results.append({"story_type": int(story.StoryType), "field_count": count, "result": outcome})
                if outcome is not None and int(outcome) != 0:
                    raise VerificationError("Office reported an unsuccessful field update: " + str(outcome))
            story = story.NextStoryRange
            visited += 1
            if visited > 256:
                raise VerificationError("Office returned a cyclic or excessive story range chain")


def refresh_document(document, info):
    info["toc_count_before_update"] = int(document.TablesOfContents.Count)
    info["field_updates"] = []
    update_story_fields(document, info["field_updates"])
    # WPS can normalize nested TOC fields, invalidating a previously read Count.
    toc_count = int(document.TablesOfContents.Count)
    for index in range(1, toc_count + 1):
        document.TablesOfContents.Item(index).Update()
    pagination = []
    for attempt in range(4):
        document.Repaginate()
        before = int(document.ComputeStatistics(2))
        update_story_fields(document, info["field_updates"])
        toc_count = int(document.TablesOfContents.Count)
        for index in range(1, toc_count + 1):
            document.TablesOfContents.Item(index).UpdatePageNumbers()
        document.Repaginate()
        after = int(document.ComputeStatistics(2))
        pagination.append({"before": before, "after": after})
        if before == after and after > 0:
            break
    else:
        raise VerificationError("Pagination did not stabilize after four field-update passes")
    info["pagination_passes"] = pagination
    info["page_count_com"] = after
    info["toc_count"] = int(document.TablesOfContents.Count)
    if info["toc_count_before_update"] and not info["toc_count"]:
        raise VerificationError("All tables of contents disappeared during field updates")
    info["toc_preview"] = []
    for index in range(1, info["toc_count"] + 1):
        text = str(document.TablesOfContents.Item(index).Range.Text)
        info["toc_preview"].append({"characters": len(text), "text": text[:800]})
        if not text.strip():
            raise VerificationError("Office left an empty table of contents")


def export_pdf(document, pdf_path, info):
    failures = []
    for method in ("ExportAsFixedFormat", "SaveAs2", "SaveAs"):
        try:
            export = getattr(document, method)
            if method == "ExportAsFixedFormat":
                export(OutputFileName=str(pdf_path), ExportFormat=17, OpenAfterExport=False, CreateBookmarks=1)
            else:
                export(FileName=str(pdf_path), FileFormat=17, AddToRecentFiles=False)
            with pdf_path.open("rb") as stream:
                if stream.read(5) != b"%PDF-":
                    raise VerificationError("Office did not produce a PDF")
            info["export_method"] = method
            info["export_fallback_errors"] = failures
            return
        except Exception as exc:
            failures.append({"method": method, "error": str(exc)})
    info["export_fallback_errors"] = failures
    raise VerificationError("PDF export failed with all supported Office methods")


def close_owned_documents(application, owned_paths, cleanup):
    # Never call Documents.Close: a user may have opened another document meanwhile.
    documents = application.Documents
    for index in range(int(documents.Count), 0, -1):
        document = documents.Item(index)
        filename = Path(str(document.FullName)).resolve()
        if filename in owned_paths:
            document.Close(0)
            cleanup["closed_documents"].append(str(filename))
    cleanup["remaining_document_count"] = int(documents.Count)
    if cleanup["remaining_document_count"] == 0:
        application.Quit(0)
        cleanup["quit_called"] = True
    else:
        raise VerificationError("An unexpected document remains open; the application was not quit")


def bind_application_process(application, before_all, new_office, executable, isolation):
    """Read-only binding; a new process elsewhere is not evidence of ownership."""
    import win32gui
    import win32process

    probes = isolation["pre_open_binding_probes"] = []
    for method in ("Application.Hwnd", "Application.ActiveWindow.Hwnd", "Application.Windows.Item(1).Hwnd", "IOleWindow.GetWindow"):
        try:
            if method == "Application.Hwnd":
                hwnd = int(application.Hwnd)
            elif method == "Application.ActiveWindow.Hwnd":
                hwnd = int(application.ActiveWindow.Hwnd)
            elif method == "Application.Windows.Item(1).Hwnd":
                hwnd = int(application.Windows.Item(1).Hwnd)
            else:
                window = application._oleobj_.QueryInterface(
                    "{00000114-0000-0000-C000-000000000046}"
                )
                hwnd = int(window.GetWindow())
        except Exception as exc:
            probes.append({"method": method, "error": str(exc)})
            continue
        if not hwnd or not win32gui.IsWindow(hwnd):
            probes.append({"method": method, "error": "No valid application window"})
            continue
        pid = win32process.GetWindowThreadProcessId(hwnd)[1]
        probes.append({"method": method, "hwnd": hwnd, "pid": pid})
        if pid in before_all or pid not in new_office:
            raise VerificationError("Application window belongs to an existing or unidentified process; no changes allowed")
        if not os.path.samefile(new_office[pid], executable):
            raise VerificationError("Application window does not belong to the activated COM executable")
        root_hwnd = win32gui.GetAncestor(hwnd, 2)
        if not root_hwnd:
            raise VerificationError("Cannot verify the application root window before opening a document")
        visible = bool(win32gui.IsWindowVisible(root_hwnd))
        if visible:
            raise VerificationError("The pre-bound application window is visible; no changes allowed")
        isolation.update({
            "pre_open_binding_method": method, "application_window_handle": hwnd,
            "application_process_id": pid, "pre_open_binding_confirmed": True,
            "pre_open_root_window_handle": root_hwnd, "pre_open_root_window_visible": visible,
        })
        return pid
    raise VerificationError(
        "Cannot bind the empty Office COM object to a new process using read-only interfaces; "
        "no state was changed, no document was opened, and the unconfirmed instance was not quit"
    )


def render_copy(paths, update_copy, report):
    if sys.platform != "win32":
        raise VerificationError("Windows with an installed Word/WPS COM server is required")
    try:
        import pythoncom
        import win32com.client
        import win32gui
        import win32process
    except ImportError as exc:
        raise VerificationError("pywin32 is required for Windows Office rendering") from exc

    candidates = installed_renderers()
    report["available_renderers"] = candidates
    if not candidates:
        raise VerificationError("No installed Word/WPS LocalServer32 executable was found")
    info = report["office"] = {"activation_errors": []}
    cleanup = info["cleanup"] = {"closed_documents": [], "quit_called": False, "errors": []}
    application = document = None
    safe_to_clean = False
    owner_pid = None
    owned_paths = {paths["working_copy"].resolve(), paths["pdf"].resolve()}
    pythoncom.CoInitialize()
    try:
        for candidate in candidates:
            before_all = set(win32process.EnumProcesses())
            before_office = office_processes()
            try:
                application = win32com.client.DispatchEx(candidate["clsid"])
                break
            except Exception as exc:
                info["activation_errors"].append({"server": candidate, "error": str(exc)})
        if application is None:
            raise VerificationError("All installed Word/WPS COM servers failed to activate")
        info["server"] = candidate
        new_office = {pid: path for pid, path in office_processes().items() if pid not in before_all}
        before_count = int(application.Documents.Count)
        info["isolation"] = {
            "existing_office_pids": sorted(before_office), "new_office_pids": sorted(new_office),
            "documents_before_open": before_count, "independent_confirmed": False,
            "pre_open_binding_confirmed": False,
        }
        require_empty_new_instance(before_count, set(new_office))
        owner_pid = bind_application_process(
            application, before_all, new_office, candidate["executable"], info["isolation"]
        )
        require_empty_new_instance(int(application.Documents.Count), {owner_pid})
        safe_to_clean = True
        application.Visible = False
        application.DisplayAlerts = 0
        application.AutomationSecurity = 3
        document = application.Documents.Open(
            str(paths["working_copy"]), ConfirmConversions=False, ReadOnly=False,
            AddToRecentFiles=False, Visible=False,
        )
        if Path(str(document.FullName)).resolve() != paths["working_copy"].resolve():
            raise VerificationError("Office did not open the expected artifact copy")
        if int(application.Documents.Count) != 1:
            raise VerificationError("The new Office instance has unexpected documents")
        hwnd = int(document.ActiveWindow.Hwnd)
        document_pid = win32process.GetWindowThreadProcessId(hwnd)[1]
        if document_pid != owner_pid:
            safe_to_clean = False
            raise VerificationError("The document window does not belong to the pre-bound application process")
        if not os.path.samefile(new_office[owner_pid], candidate["executable"]):
            raise VerificationError("The document window does not belong to the activated COM server")
        root_hwnd = win32gui.GetAncestor(hwnd, 2)
        root_visible = bool(win32gui.IsWindowVisible(root_hwnd))
        info["isolation"].update({
            "document_window_handle": hwnd, "document_window_pid": owner_pid,
            "application_visible": bool(application.Visible), "root_window_visible": root_visible,
            "independent_confirmed": True,
        })
        if bool(application.Visible) or root_visible:
            raise VerificationError("The private Office instance is not hidden")
        refresh_document(document, info)
        if update_copy:
            document.Save()
        info["updated_copy_saved"] = bool(update_copy)
        export_pdf(document, paths["pdf"], info)
    finally:
        if application is not None and safe_to_clean:
            try:
                close_owned_documents(application, owned_paths, cleanup)
            except Exception as exc:
                cleanup["errors"].append(str(exc))
        elif application is not None:
            cleanup["errors"].append("Instance ownership is unconfirmed; it was not changed or quit")
        document = application = None
        gc.collect()
        pythoncom.CoUninitialize()
        if owner_pid is not None and cleanup["quit_called"]:
            deadline = time.monotonic() + 5
            while owner_pid in win32process.EnumProcesses() and time.monotonic() < deadline:
                time.sleep(0.1)
            cleanup["document_process_exited"] = owner_pid not in win32process.EnumProcesses()
        cleanup["office_pids_after"] = sorted(office_processes())
    if cleanup["errors"] or not cleanup["quit_called"] or cleanup.get("document_process_exited") is not True:
        raise VerificationError("The private Office session was not cleanly closed or its bound process did not exit")


def inspect_pdf(paths, sample_count=8, dpi=144):
    try:
        import pymupdf as fitz
    except ImportError:
        try:
            import fitz
        except ImportError as exc:
            raise VerificationError("PyMuPDF is required to inspect the rendered PDF") from exc
    try:
        from PIL import Image, ImageChops, ImageDraw
    except ImportError as exc:
        raise VerificationError("Pillow is required to produce raster previews") from exc

    result = {"pages": [], "samples": [], "boundary_tolerance_pt": 1.0}
    issues = []
    with fitz.open(paths["pdf"]) as pdf:
        if pdf.is_encrypted or pdf.page_count == 0:
            raise VerificationError("Office produced an encrypted or empty PDF")
        result["page_count"] = pdf.page_count
        for index, page in enumerate(pdf):
            page_box = tuple(page.rect)
            flags = fitz.TEXTFLAGS_DICT & ~fitz.TEXT_PRESERVE_IMAGES
            text = page.get_text("dict", clip=fitz.INFINITE_RECT(), flags=flags)
            spans = [span for block in text["blocks"] if block["type"] == 0
                     for line in block["lines"] for span in line["spans"] if span["text"].strip()]
            images = page.get_image_info()
            text_boxes = [tuple(span["bbox"]) for span in spans]
            text_bbox = None
            if text_boxes:
                text_bbox = [min(box[0] for box in text_boxes), min(box[1] for box in text_boxes),
                             max(box[2] for box in text_boxes), max(box[3] for box in text_boxes)]
            bad_text = [{"bbox_pt": list(span["bbox"]), "text": span["text"][:120]}
                        for span in spans if out_of_bounds(span["bbox"], page_box)]
            image_boxes = [list(image["bbox"]) for image in images]
            bad_images = [box for box in image_boxes if out_of_bounds(box, page_box)]
            number = index + 1
            result["pages"].append({
                "page": number, "page_box_pt": list(page_box), "text_span_count": len(spans),
                "text_bbox_pt": text_bbox, "image_count": len(images), "image_bboxes_pt": image_boxes,
                "out_of_bounds_text": bad_text, "out_of_bounds_images": bad_images,
            })
            if bad_text or bad_images:
                issues.append({"page": number, "reason": "content_outside_page",
                               "text_count": len(bad_text), "image_count": len(bad_images)})
            if not spans and not images and not page.get_drawings():
                issues.append({"page": number, "reason": "empty_page"})
        result["image_occurrences"] = sum(page["image_count"] for page in result["pages"])
        result["image_pages"] = [page["page"] for page in result["pages"] if page["image_count"]]
        selected = select_sample_pages(result["pages"], sample_count)
        thumbnails = []
        for number in selected:
            pixmap = pdf[number - 1].get_pixmap(dpi=dpi, alpha=False, colorspace=fitz.csRGB)
            image = Image.frombytes("RGB", (pixmap.width, pixmap.height), pixmap.samples)
            filename = paths["run_dir"] / ("pdf-page-%04d.png" % number)
            image.save(filename)
            ink_box = ImageChops.difference(image, Image.new("RGB", image.size, "white")).getbbox()
            histogram = image.convert("L").histogram()
            ink_fraction = sum(histogram[:245]) / (image.width * image.height)
            result["samples"].append({
                "page": number, "png": str(filename), "size_px": list(image.size),
                "nonwhite_bbox_px": list(ink_box) if ink_box else None,
                "nonwhite_fraction": round(ink_fraction, 6),
            })
            if ink_box is None:
                issues.append({"page": number, "reason": "blank_raster_preview"})
            thumbnail = image.copy()
            thumbnail.thumbnail((380, 535))
            thumbnails.append((number, thumbnail))
        if thumbnails:
            sheet = Image.new("RGB", (800, math.ceil(len(thumbnails) / 2) * 570), "#e8eaed")
            draw = ImageDraw.Draw(sheet)
            for index, (number, thumbnail) in enumerate(thumbnails):
                x, y = (index % 2) * 400, (index // 2) * 570
                image_count = result["pages"][number - 1]["image_count"]
                draw.text((x + 10, y + 8), "Page %d | images: %d" % (number, image_count), fill="black")
                sheet.paste(thumbnail, (x + (400 - thumbnail.width) // 2, y + 28))
            sheet.save(paths["contact_sheet"])
            result["contact_sheet"] = str(paths["contact_sheet"])
    result["issues"] = issues
    result["geometry_check_passed"] = not issues
    result["review_limitations"] = (
        "Text bounds cover PDF text spans, not text inside bitmaps. Image counts are rendered "
        "raster occurrences, not unique DOCX media. Bounds and previews do not certify all visual layout."
    )
    return result


if __name__ == "__main__":
    raise SystemExit(main())
