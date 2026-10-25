"""Generates the dense INDEXED / RELATIVE file fixtures: seeded random statement sequences.

Run: uv run python tests/equivalence/generate_dense_files.py, then record_fixtures.py in Docker.
Small key spaces make duplicates, missing records, boundaries and repositioning frequent, so
GnuCOBOL's handling of each status and of the file position is exercised against the runtime.
"""

from __future__ import annotations

import random
import sys
from pathlib import Path

FIXTURES = Path(__file__).resolve().parents[2] / "translators/cobol-to-java/fixtures"
SEED = 20261025


def cobol(program_id: str, select: str, fd: str, ws: str, body: list[str]) -> str:
    lines = [
        "       IDENTIFICATION DIVISION.",
        f"       PROGRAM-ID. {program_id}.",
        "       ENVIRONMENT DIVISION.",
        "       INPUT-OUTPUT SECTION.",
        "       FILE-CONTROL.",
        *select.splitlines(),
        "       DATA DIVISION.",
        "       FILE SECTION.",
        *fd.splitlines(),
        "       WORKING-STORAGE SECTION.",
        *ws.splitlines(),
        "       PROCEDURE DIVISION.",
        *("           " + b for b in body),
        "           STOP RUN.",
    ]
    for line in lines:
        assert len(line) <= 72, line
    return "\n".join(lines) + "\n"


def indexed(rng: random.Random, n: int) -> str:
    keys = "ABC"
    groups = "XY"
    unq = ["P1", "P2", "Q1", "Q2", "R1"]

    def rec() -> str:
        return (rng.choice(keys) + rng.choice(keys) + rng.choice(groups) + rng.choice(unq) + f"{rng.randrange(1000):03d}")

    body = ["OPEN OUTPUT DFILE. CLOSE DFILE.", "OPEN I-O DFILE. DISPLAY \"open \" ST."]
    seq_open = False
    for i in range(n):
        tag = f'"{i:03d} '
        show = f'DISPLAY {tag}" ST " " D-REC.'
        op = rng.random()
        if op < 0.18:
            body += [f'MOVE "{rec()}" TO D-REC.', "WRITE D-REC.", show.replace(tag, tag + "W ")]
        elif op < 0.28:
            body += [f'MOVE "{rec()}" TO D-REC.', "REWRITE D-REC.", show.replace(tag, tag + "RW ")]
        elif op < 0.33:
            body += [f'MOVE "{rng.choice(keys)}{rng.choice(keys)}" TO D-KEY.', "DELETE DFILE.", show.replace(tag, tag + "DEL ")]
        elif op < 0.40:
            body += [f'MOVE "{rng.choice(keys)}{rng.choice(keys)}" TO D-KEY.', "READ DFILE.", show.replace(tag, tag + "RK ")]
        elif op < 0.44:
            body += [f'MOVE "{rng.choice(groups)}" TO D-GRP.', "READ DFILE KEY IS D-GRP.", show.replace(tag, tag + "RG ")]
        elif op < 0.47:
            body += [f'MOVE "{rng.choice(unq)}" TO D-UNQ.', "READ DFILE KEY IS D-UNQ.", show.replace(tag, tag + "RU ")]
        elif op < 0.65:
            body += ["READ DFILE NEXT.", show.replace(tag, tag + "RN ")]
        elif op < 0.75:
            body += ["READ DFILE PREVIOUS.", show.replace(tag, tag + "RP ")]
        elif op < 0.93:
            cond = rng.choice(["=", ">", ">=", "<", "<=", "NOT <", "NOT >"])
            field, value = rng.choice([
                ("D-KEY", rng.choice(keys) + rng.choice(keys + "@")),
                ("D-K1", rng.choice(keys + "@D")),
                ("D-GRP", rng.choice(groups + "AZ")),
                ("D-UNQ", rng.choice(unq + ["P0", "S1"])),
            ])
            body += [f'MOVE "{value}" TO {field}.', f"START DFILE KEY {cond} {field}.", show.replace(tag, tag + "ST ")]
        elif op < 0.97:
            body += ["CLOSE DFILE. OPEN I-O DFILE.", show.replace(tag, tag + "REOPEN ")]
        else:
            body += ["CLOSE DFILE. OPEN INPUT DFILE.", "READ DFILE NEXT.", show.replace(tag, tag + "IN "),
                     'MOVE "AAX" TO D-REC. WRITE D-REC.', show.replace(tag, tag + "INW "),
                     "CLOSE DFILE. OPEN I-O DFILE."]
        # A second file in ACCESS SEQUENTIAL, opened now and then in another mode.
        if rng.random() < 0.25:
            mode = rng.choice(["OUTPUT", "EXTEND", "I-O", "INPUT"])
            sop = rng.random()
            stag = f'"{i:03d}S '
            sshow = f'DISPLAY {stag}" SS " " S-REC.'
            if not seq_open or rng.random() < 0.2:
                if seq_open:
                    body.append("CLOSE SFILE.")
                body += [f"OPEN {mode} SFILE.", sshow.replace(stag, stag + "OPEN ")]
                seq_open = True
            elif sop < 0.35:
                body += [f'MOVE "{rng.randrange(10)}{rng.choice("abc")}" TO S-REC.', "WRITE S-REC.", sshow.replace(stag, stag + "W ")]
            elif sop < 0.6:
                body += ["READ SFILE.", sshow.replace(stag, stag + "R ")]
            elif sop < 0.75:
                body += [f'MOVE "{rng.randrange(10)}{rng.choice("xyz")}" TO S-REC.', "REWRITE S-REC.", sshow.replace(stag, stag + "RW ")]
            elif sop < 0.85:
                body += ["DELETE SFILE.", sshow.replace(stag, stag + "DEL ")]
            else:
                cond = rng.choice(["=", ">", ">=", "<", "<="])
                body += [f'MOVE "{rng.randrange(10)}" TO S-KEY.', f"START SFILE KEY {cond} S-KEY.", sshow.replace(stag, stag + "ST ")]
    body += ["CLOSE DFILE.", "CLOSE SFILE."]
    select = """           SELECT DFILE ASSIGN TO "dense.dat"
               ORGANIZATION IS INDEXED
               ACCESS MODE IS DYNAMIC
               RECORD KEY IS D-KEY
               ALTERNATE RECORD KEY IS D-GRP WITH DUPLICATES
               ALTERNATE RECORD KEY IS D-UNQ
               FILE STATUS IS ST.
           SELECT SFILE ASSIGN TO "dseq.dat"
               ORGANIZATION IS INDEXED
               ACCESS MODE IS SEQUENTIAL
               RECORD KEY IS S-KEY
               FILE STATUS IS SS."""
    fd = """       FD DFILE.
       01 D-REC.
          05 D-KEY.
             10 D-K1 PIC X.
             10 D-K2 PIC X.
          05 D-GRP   PIC X.
          05 D-UNQ   PIC X(2).
          05 D-VAL   PIC 9(3).
       FD SFILE.
       01 S-REC.
          05 S-KEY   PIC X.
          05 S-VAL   PIC X."""
    ws = """       01 ST PIC XX.
       01 SS PIC XX."""
    return cobol("IDXDENSE", select, fd, ws, body)


def relative(rng: random.Random, n: int) -> str:
    body = ["OPEN OUTPUT RELF. CLOSE RELF.", "OPEN I-O RELF. DISPLAY \"open \" ST."]
    dynamic = True
    for i in range(n):
        tag = f'"{i:03d} '
        if dynamic:
            show = f'DISPLAY {tag}" ST " " RK " " R-REC.'
            op = rng.random()
            num = f"MOVE {rng.randrange(0, 12)} TO RK."
            if op < 0.2:
                body += [num, f'MOVE "w{i:03d}" TO R-REC.', "WRITE R-REC.", show.replace(tag, tag + "W ")]
            elif op < 0.3:
                body += [num, f'MOVE "r{i:03d}" TO R-REC.', "REWRITE R-REC.", show.replace(tag, tag + "RW ")]
            elif op < 0.38:
                body += [num, "DELETE RELF.", show.replace(tag, tag + "DEL ")]
            elif op < 0.48:
                body += [num, "READ RELF.", show.replace(tag, tag + "RK ")]
            elif op < 0.66:
                body += ["READ RELF NEXT.", show.replace(tag, tag + "RN ")]
            elif op < 0.76:
                body += ["READ RELF PREVIOUS.", show.replace(tag, tag + "RP ")]
            elif op < 0.92:
                cond = rng.choice(["=", ">", ">=", "<", "<=", "NOT <", "NOT >"])
                body += [num, f"START RELF KEY {cond} RK.", show.replace(tag, tag + "ST ")]
            elif op < 0.96:
                body += ["CLOSE RELF. OPEN I-O RELF.", show.replace(tag, tag + "REOPEN ")]
            else:
                body += ["CLOSE RELF.", f"OPEN {rng.choice(['I-O', 'INPUT', 'EXTEND'])} QFILE.",
                         f'DISPLAY {tag}QOPEN " QS.']
                dynamic = False
        else:
            show = f'DISPLAY {tag}Q" QS " " QK " " Q-REC.'
            op = rng.random()
            if op < 0.3:
                body += ["READ QFILE.", show.replace(tag, tag + "R ")]
            elif op < 0.45:
                body += [f'MOVE "q{i:03d}" TO Q-REC.', "REWRITE Q-REC.", show.replace(tag, tag + "RW ")]
            elif op < 0.55:
                body += ["DELETE QFILE.", show.replace(tag, tag + "DEL ")]
            elif op < 0.7:
                body += [f'MOVE "q{i:03d}" TO Q-REC.', "WRITE Q-REC.", show.replace(tag, tag + "W ")]
            elif op < 0.85:
                cond = rng.choice(["=", ">", ">=", "<", "<="])
                body += [f"MOVE {rng.randrange(0, 12)} TO QK.", f"START QFILE KEY {cond} QK.", show.replace(tag, tag + "ST ")]
            else:
                body += ["CLOSE QFILE. OPEN I-O RELF.", show.replace(tag, tag + "BACK ")]
                dynamic = True
    body += ["CLOSE RELF.", "CLOSE QFILE."]
    select = """           SELECT RELF ASSIGN TO "drel.dat"
               ORGANIZATION IS RELATIVE
               ACCESS MODE IS DYNAMIC
               RELATIVE KEY IS RK
               FILE STATUS IS ST.
           SELECT QFILE ASSIGN TO "drel.dat"
               ORGANIZATION IS RELATIVE
               ACCESS MODE IS SEQUENTIAL
               RELATIVE KEY IS QK
               FILE STATUS IS QS."""
    fd = """       FD RELF.
       01 R-REC PIC X(4).
       FD QFILE.
       01 Q-REC PIC X(4)."""
    ws = """       01 ST PIC XX.
       01 QS PIC XX.
       01 RK PIC 99.
       01 QK PIC 9."""
    return cobol("RELDENSE", select, fd, ws, body)


def write(name: str, program_id: str, source: str, files: dict[str, str], description: str) -> None:
    case = FIXTURES / name
    (case / "src").mkdir(parents=True, exist_ok=True)
    (case / "src" / f"{program_id}.cbl").write_text(source)
    assignments = ", ".join(f'"{k}": "{v}"' for k, v in files.items())
    (case / "case.json").write_text(
        "{\n"
        f'  "description": "{description}",\n'
        '  "expect": {"status": "ok"},\n'
        f'  "dialect": {{"encoding": "ascii", "collating_sequence": "native", "file_assignments": {{{assignments}}}}},\n'
        f'  "equivalence": {{"programs": ["{program_id}.cbl"], "main_class": "fixtures.{name}.{program_id.capitalize()}"}},\n'
        f'  "layout": {{"base_package": "fixtures.{name}"}}\n'
        "}\n")


def main() -> None:
    # Another seed (argument) explores other sequences; the committed fixtures use SEED.
    rng = random.Random(int(sys.argv[1]) if len(sys.argv) > 1 else SEED)
    write("indexed_dense", "IDXDENSE", indexed(rng, 500), {"dense.dat": "dense.dat", "dseq.dat": "dseq.dat"},
          "Dense INDEXED fixture (generate_dense_files.py): 500 seeded random statements on a DYNAMIC file with "
          "duplicate and unique alternate keys and on a SEQUENTIAL file, statuses and records after each")
    write("relative_dense", "RELDENSE", relative(rng, 500), {"drel.dat": "drel.dat"},
          "Dense RELATIVE fixture (generate_dense_files.py): 500 seeded random statements through a DYNAMIC and a "
          "SEQUENTIAL connector of one file, statuses, RELATIVE KEYs and records after each")


if __name__ == "__main__":
    main()
