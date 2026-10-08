"""Pure SOP dictionary classification for the 57 rebuild catalog processes.

New or misspelled process codes must be reviewed explicitly instead of silently
falling back to a production SOP. Importing this module performs no I/O.
"""
from __future__ import annotations

_INSPECTION_CODES = frozenset({
    "FUNC_TEST", "AGING", "WP_TEST", "PRECISION", "BALANCE", "ICT",
    "WATER_PROOF", "QC",
})
_PRODUCTION_CODES = frozenset({
    "LASER", "PLASMA", "SHEAR", "SAW", "PUNCH", "BEND", "ROLL", "DRAW",
    "FORGE", "TURN", "MILL", "DRILL", "GRIND", "BORE", "HOB", "SHAPE",
    "WEDM", "EDM", "CAST", "DIE_CAST", "MOLD", "BLOW", "TIG", "MIG",
    "SPOT", "LASER_WELD", "QUENCH", "TEMPER", "CARBURIZE", "AGE", "PLATE",
    "ANODIZE", "POWDER", "PAINT", "SCREEN", "POLISH", "SMT", "INSERT",
    "WAVE", "SOLDER_MANUAL", "WINDING", "VARNISH", "FLASH", "ASM",
    "PRESS_FIT", "DISPENSE", "PKG", "DEBURR",
})


def sop_type_for_process(process_code: str) -> str:
    """Return a valid SOP type for an exact known code; reject unknown input."""
    if isinstance(process_code, str):
        if process_code in _INSPECTION_CODES:
            return "INSPECTION"
        if process_code == "CLEAN":
            return "CLEAN"
        if process_code in _PRODUCTION_CODES:
            return "PRODUCTION"
    raise ValueError(f"Unknown SOP process code: {process_code!r}")
