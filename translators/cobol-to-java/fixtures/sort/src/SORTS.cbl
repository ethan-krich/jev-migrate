       IDENTIFICATION DIVISION.
       PROGRAM-ID. SORTS.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT PEOPLE ASSIGN TO "people.txt"
               ORGANIZATION IS LINE SEQUENTIAL.
           SELECT SORTED ASSIGN TO "sorted.txt"
               ORGANIZATION IS LINE SEQUENTIAL.
           SELECT ODD-F ASSIGN TO "odd.txt"
               ORGANIZATION IS LINE SEQUENTIAL.
           SELECT EVEN-F ASSIGN TO "even.txt"
               ORGANIZATION IS LINE SEQUENTIAL.
           SELECT WORK ASSIGN TO "work.tmp".
       DATA DIVISION.
       FILE SECTION.
       FD PEOPLE.
       01 P-REC        PIC X(10).
       FD SORTED.
       01 S-REC        PIC X(10).
       FD ODD-F.
       01 O-REC        PIC X(3).
       FD EVEN-F.
       01 E-REC        PIC X(3).
       SD WORK.
       01 W-REC.
          05 W-NAME    PIC X(7).
          05 W-AGE     PIC 99.
          05 FILLER    PIC X.
       WORKING-STORAGE SECTION.
       01 EOF          PIC X VALUE "N".
       01 N            PIC 9 VALUE 0.
       01 LINE-OUT     PIC X(10).
       01 CAT-REC      PIC X(10) VALUE "cat    01 ".
       PROCEDURE DIVISION.
       MAIN-PARA.
           SORT WORK ON DESCENDING KEY W-AGE ASCENDING KEY W-NAME
               WITH DUPLICATES IN ORDER
               USING PEOPLE GIVING SORTED.
           OPEN INPUT SORTED.
           PERFORM UNTIL EOF = "Y"
               READ SORTED AT END MOVE "Y" TO EOF
                   NOT AT END DISPLAY "[" S-REC "]"
               END-READ
           END-PERFORM.
           CLOSE SORTED.
           SORT WORK ON ASCENDING KEY W-AGE
               WITH DUPLICATES IN ORDER
               INPUT PROCEDURE IS MAKE-RECORDS
               OUTPUT PROCEDURE IS SHOW-RECORDS.
           MERGE WORK ON ASCENDING KEY W-NAME
               USING ODD-F EVEN-F
               OUTPUT PROCEDURE IS SHOW-RECORDS.
      * Without DUPLICATES IN ORDER GnuCOBOL still returns equal keys
      * in the order they were released.
           SORT WORK ON ASCENDING KEY W-AGE
               USING PEOPLE GIVING SORTED.
           MOVE "N" TO EOF.
           OPEN INPUT SORTED.
           PERFORM UNTIL EOF = "Y"
               READ SORTED AT END MOVE "Y" TO EOF
                   NOT AT END DISPLAY "by age [" S-REC "]"
               END-READ
           END-PERFORM.
           CLOSE SORTED.
           SORT WORK ON DESCENDING KEY W-AGE
               INPUT PROCEDURE IS MAKE-TIES
               OUTPUT PROCEDURE IS SHOW-RECORDS.
           STOP RUN.
       MAKE-TIES.
           MOVE "r1     05 " TO W-REC. RELEASE W-REC.
           MOVE "r2     03 " TO W-REC. RELEASE W-REC.
           MOVE "r3     05 " TO W-REC. RELEASE W-REC.
           MOVE "r4     03 " TO W-REC. RELEASE W-REC.
           MOVE "r5     05 " TO W-REC. RELEASE W-REC.
           MOVE "r6     08 " TO W-REC. RELEASE W-REC.
           MOVE "r7     03 " TO W-REC. RELEASE W-REC.
           MOVE "r8     05 " TO W-REC. RELEASE W-REC.
       MAKE-RECORDS.
           MOVE "zed    09 " TO W-REC. RELEASE W-REC.
           MOVE "amy    07 " TO W-REC. RELEASE W-REC.
           MOVE "bob    09 " TO W-REC. RELEASE W-REC.
           RELEASE W-REC FROM CAT-REC.
       SHOW-RECORDS.
           MOVE "N" TO EOF.
           PERFORM UNTIL EOF = "Y"
               RETURN WORK INTO LINE-OUT
                   AT END MOVE "Y" TO EOF
                   NOT AT END DISPLAY "returned [" LINE-OUT "] " W-AGE
               END-RETURN
           END-PERFORM.
