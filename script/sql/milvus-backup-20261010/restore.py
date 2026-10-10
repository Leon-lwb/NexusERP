#!/usr/bin/env python3
"""Verify a Nexus Milvus backup, or restore into new collections."""
import argparse
import gzip
import hashlib
import json
import math
import os
from pathlib import Path
import sys
import uuid

ROOT = Path(__file__).resolve().parent


def content_hash(rows, primary):
    digest = hashlib.sha256()
    for row in sorted(rows, key=lambda item: item[primary]):
        digest.update(json.dumps(row, ensure_ascii=False, sort_keys=True,
                                 separators=(",", ":"), allow_nan=False).encode("utf-8"))
        digest.update(b"\n")
    return digest.hexdigest()


def read_rows(partition):
    path = (ROOT / partition["data_file"]).resolve()
    if ROOT not in path.parents:
        raise ValueError("Data file must stay inside the backup directory")
    if hashlib.sha256(path.read_bytes()).hexdigest() != partition["sha256"]:
        raise ValueError(f"Checksum mismatch: {partition['data_file']}")
    with gzip.open(path, "rt", encoding="utf-8") as handle:
        rows = [json.loads(line) for line in handle if line.strip()]
    if len(rows) != partition["row_count"]:
        raise ValueError(f"Row count mismatch: {partition['data_file']}")
    return rows


def verify_backup():
    manifest = json.loads((ROOT / "manifest.json").read_text(encoding="utf-8"))
    if manifest.get("format") != "nexus-milvus-logical-backup" or manifest.get("format_version") != 1:
        raise ValueError("Unsupported backup format")
    if not manifest.get("complete"):
        raise ValueError("Backup is incomplete")
    for database in manifest["databases"]:
        for saved in database["collections"]:
            schema = saved["schema"]
            if schema.get("auto_id"):
                raise ValueError("This tool requires explicitly assigned primary keys")
            primary = next(field["name"] for field in schema["fields"] if field.get("is_primary"))
            seen = set()
            total = 0
            for partition in saved["partitions"]:
                rows = read_rows(partition)
                if content_hash(rows, primary) != partition["content_sha256"]:
                    raise ValueError(f"Content mismatch: {partition['data_file']}")
                for row in rows:
                    if row[primary] in seen:
                        raise ValueError(f"Duplicate primary key in {saved['name']}")
                    seen.add(row[primary])
                    for field in schema["fields"]:
                        value = row[field["name"]]
                        if field["type"] == 101:
                            if len(value) != int(field["params"]["dim"]) or not all(math.isfinite(v) for v in value):
                                raise ValueError(f"Invalid vector in {saved['name']}")
                total += len(rows)
            if total != saved["row_count"]:
                raise ValueError(f"Collection row count mismatch: {saved['name']}")
            print(f"Verified {database['name']}.{saved['name']}: {total} rows")
    return manifest


def query_partition(collection, partition, fields):
    iterator = collection.query_iterator(expr="", output_fields=fields,
        partition_names=[partition], batch_size=128, consistency_level="Strong", timeout=120)
    try:
        rows = []
        while True:
            batch = iterator.next()
            if not batch:
                return rows
            rows.extend(batch)
    finally:
        iterator.close()


def restore_backup(manifest, uri, target_database):
    try:
        from pymilvus import Collection, CollectionSchema, connections, db, utility
    except ImportError as exc:
        raise RuntimeError("Install dependencies first: python -m pip install -r requirements.txt") from exc
    if target_database and len(manifest["databases"]) != 1:
        raise ValueError("--database can only override a backup with one database")
    alias = "nexus_restore_" + uuid.uuid4().hex
    connect_args = {"alias": alias, "uri": uri, "timeout": 30}
    token = os.environ.get("MILVUS_TOKEN")
    if token:
        connect_args["token"] = token
    connections.connect(**connect_args)
    try:
        existing_databases = set(db.list_database(using=alias))
        targets = [(target_database or item["name"], item) for item in manifest["databases"]]
        # Check all names before creating or inserting anything.
        for target, saved_database in targets:
            if target not in existing_databases:
                continue
            db.using_database(target, using=alias)
            for saved in saved_database["collections"]:
                for name in [saved["name"], *saved["aliases"]]:
                    if utility.has_collection(name, using=alias):
                        raise RuntimeError(f"Refusing to overwrite existing collection or alias: {target}.{name}")
        for target, saved_database in targets:
            if target not in existing_databases:
                db.create_database(target, using=alias)
            db.using_database(target, using=alias)
            for saved in saved_database["collections"]:
                schema = CollectionSchema.construct_from_dict(saved["schema"])
                collection = Collection(saved["name"], schema=schema, using=alias,
                    num_shards=saved["settings"]["num_shards"],
                    consistency_level=saved["settings"]["consistency_level"])
                for partition in saved["partitions"]:
                    name = partition["name"]
                    if name != "_default":
                        collection.create_partition(name)
                    rows = read_rows(partition)
                    for start in range(0, len(rows), 128):
                        batch = rows[start:start + 128]
                        result = collection.insert(batch, partition_name=name, timeout=120)
                        if result.insert_count != len(batch):
                            raise RuntimeError(f"Incomplete insert into {target}.{saved['name']}")
                collection.flush(timeout=120)
                for index in saved["indexes"]:
                    collection.create_index(index["field"], index["index_param"],
                                            index_name=index["index_name"], timeout=120)
                properties = saved["settings"].get("properties", {})
                if properties:
                    collection.set_properties(properties)
                collection.load(timeout=120)
                primary = schema.primary_field.name
                fields = [field.name for field in schema.fields]
                total = 0
                for partition in saved["partitions"]:
                    restored_rows = query_partition(collection, partition["name"], fields)
                    if len(restored_rows) != partition["row_count"]:
                        raise RuntimeError(f"Restored row count mismatch: {target}.{saved['name']}")
                    if content_hash(restored_rows, primary) != partition["content_sha256"]:
                        raise RuntimeError(f"Restored content mismatch: {target}.{saved['name']}")
                    total += len(restored_rows)
                for name in saved["aliases"]:
                    utility.create_alias(saved["name"], name, using=alias)
                print(f"Restored and verified {target}.{saved['name']}: {total} rows")
    finally:
        connections.disconnect(alias)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify-only", action="store_true", help="Check local backup without connecting to Milvus")
    parser.add_argument("--uri", default=os.environ.get("MILVUS_URI", "http://localhost:19530"))
    parser.add_argument("--database", help="Restore into this database instead of the original database")
    args = parser.parse_args()
    manifest = verify_backup()
    if not args.verify_only:
        restore_backup(manifest, args.uri, args.database)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
