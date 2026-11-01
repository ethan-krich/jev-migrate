       IDENTIFICATION DIVISION.
       PROGRAM-ID. SRCHALL.
      * SEARCH ALL is GnuCOBOL's binary search whatever the keys hold:
      * duplicates find the occurrence its probes reach first, and keys
      * out of order can miss a value that is in the table.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 SORTED-T.
          05 FILLER PIC X(12) VALUE "010203040506".
       01 SORTED-R REDEFINES SORTED-T.
          05 S-KEY  PIC 99 OCCURS 6 ASCENDING KEY S-KEY INDEXED BY SX.
       01 DUPS-T.
          05 FILLER PIC X(14) VALUE "1A2B2C2D2E2F3G".
       01 DUPS-R REDEFINES DUPS-T.
          05 D-ENT  OCCURS 7 ASCENDING KEY D-KEY INDEXED BY DX.
             10 D-KEY  PIC 9.
             10 D-TAG  PIC X.
       01 MIXED-T.
          05 FILLER PIC X(10) VALUE "0905010703".
       01 MIXED-R REDEFINES MIXED-T.
          05 M-KEY  PIC 99 OCCURS 5 ASCENDING KEY M-KEY INDEXED BY MX.
       01 DOWN-T.
          05 FILLER PIC X(8) VALUE "DCBAdcba".
       01 DOWN-R REDEFINES DOWN-T.
          05 W-ENT  OCCURS 4 DESCENDING KEY W-KEY INDEXED BY WX.
             10 W-KEY  PIC X.
             10 FILLER PIC X.
       01 N         PIC 99.
       01 IX-N      PIC 9.
       PROCEDURE DIVISION.
       MAIN-PARA.
           PERFORM VARYING N FROM 0 BY 1 UNTIL N > 7
               SEARCH ALL S-KEY
                   AT END SET IX-N TO SX
                          DISPLAY "sorted " N " not found, index " IX-N
                   WHEN S-KEY(SX) = N
                          SET IX-N TO SX
                          DISPLAY "sorted " N " at " IX-N
               END-SEARCH
           END-PERFORM.
           SEARCH ALL D-ENT
               AT END DISPLAY "dup 2 not found"
               WHEN D-KEY(DX) = 2 DISPLAY "dup 2 tag " D-TAG(DX)
           END-SEARCH.
           PERFORM VARYING N FROM 1 BY 2 UNTIL N > 9
               SEARCH ALL M-KEY
                   AT END DISPLAY "mixed " N " not found"
                   WHEN M-KEY(MX) = N
                          SET IX-N TO MX
                          DISPLAY "mixed " N " at " IX-N
               END-SEARCH
           END-PERFORM.
           SEARCH ALL W-ENT
               AT END DISPLAY "down B not found"
               WHEN W-KEY(WX) = "B"
                   SET IX-N TO WX
                   DISPLAY "down B at " IX-N
           END-SEARCH.
           SEARCH ALL W-ENT
               WHEN W-KEY(WX) = "Z" DISPLAY "down Z found"
           END-SEARCH.
           DISPLAY "after down Z".
           STOP RUN.
