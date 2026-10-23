from __future__ import annotations

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent))

from support import CORE, WORKER  # noqa: E402


@pytest.fixture(scope="session")
def built() -> None:
    """Integration tests need the C++ core and the Java worker (./scripts/build.sh)."""
    missing = [str(p) for p in (CORE, WORKER) if not p.exists()]
    if missing:
        pytest.fail(f"build artefacts missing: {missing}; run ./scripts/build.sh", pytrace=False)
