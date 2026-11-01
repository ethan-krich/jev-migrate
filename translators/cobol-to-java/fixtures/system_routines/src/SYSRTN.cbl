       IDENTIFICATION DIVISION.
       PROGRAM-ID. SYSRTN.
      * GnuCOBOL's CBL_ system routines.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-TEXT      PIC X(12) VALUE "Hello, World".
       01 WS-ACCENT    PIC X(4) VALUE X"E9614120".
       01 WS-N         PIC 99 VALUE 5.
       01 WS-MASK      PIC X(4) VALUE X"DFDFDFDF".
       01 WS-SPACES    PIC X(4) VALUE X"20202020".
       01 WS-WORK      PIC X(4).
       PROCEDURE DIVISION.
       MAIN-PARA.
           CALL "CBL_TOUPPER" USING WS-TEXT BY VALUE WS-N.
           DISPLAY "upper 5  [" WS-TEXT "] rc " RETURN-CODE.
           CALL "CBL_TOLOWER" USING WS-TEXT
               BY VALUE LENGTH OF WS-TEXT
               NOT ON EXCEPTION DISPLAY "lower ok"
           END-CALL.
           DISPLAY "lower    [" WS-TEXT "]".
           CALL "CBL_TOUPPER" USING WS-ACCENT BY VALUE 4.
           DISPLAY "accent   [" WS-ACCENT "]".
           MOVE "abcd" TO WS-WORK.
           CALL "CBL_AND" USING WS-MASK WS-WORK BY VALUE 4.
           DISPLAY "and      [" WS-WORK "]".
           CALL "CBL_OR" USING WS-SPACES WS-WORK BY VALUE 2.
           DISPLAY "or       [" WS-WORK "]".
           CALL "CBL_XOR" USING WS-SPACES WS-WORK BY VALUE 4.
           DISPLAY "xor      [" WS-WORK "]".
           CALL "CBL_NOT" USING WS-WORK BY VALUE 4.
           CALL "CBL_NOT" USING WS-WORK BY VALUE 3.
           DISPLAY "not      [" WS-WORK "]".
           MOVE "abcd" TO WS-WORK.
           CALL "CBL_EQ" USING WS-MASK WS-WORK BY VALUE 4.
           DISPLAY "eq       [" WS-WORK "]".
           MOVE "abcd" TO WS-WORK.
           CALL "CBL_IMP" USING WS-MASK WS-WORK BY VALUE 4.
           DISPLAY "imp      [" WS-WORK "]".
           MOVE "abcd" TO WS-WORK.
           CALL "CBL_NIMP" USING WS-MASK WS-WORK BY VALUE 4.
           DISPLAY "nimp     [" WS-WORK "]".
           MOVE 7 TO RETURN-CODE.
           CALL "CBL_TOLOWER" USING WS-WORK BY VALUE 0.
           DISPLAY "rc after " RETURN-CODE.
           STOP RUN.
