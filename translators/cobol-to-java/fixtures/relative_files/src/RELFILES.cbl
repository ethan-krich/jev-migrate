       IDENTIFICATION DIVISION.
       PROGRAM-ID. RELFILES.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT RELF ASSIGN TO "rel.dat"
               ORGANIZATION IS RELATIVE
               ACCESS MODE IS DYNAMIC
               RELATIVE KEY IS RK
               FILE STATUS IS ST.
           SELECT QF ASSIGN TO "rel.dat"
               ORGANIZATION IS RELATIVE
               ACCESS MODE IS SEQUENTIAL
               RELATIVE KEY IS QK
               FILE STATUS IS QS.
           SELECT NOSF ASSIGN TO "nost.dat"
               ORGANIZATION IS RELATIVE
               ACCESS MODE IS RANDOM
               RELATIVE KEY IS NK.
       DATA DIVISION.
       FILE SECTION.
       FD RELF.
       01 R-REC PIC X(5).
       FD QF.
       01 Q-REC PIC X(5).
       FD NOSF.
       01 N-REC PIC X(2).
       WORKING-STORAGE SECTION.
       01 ST PIC XX.
       01 QS PIC XX.
       01 RK PIC 9(3).
       01 QK PIC 99.
       01 NK PIC 9 VALUE 1.
       PROCEDURE DIVISION.
           OPEN OUTPUT QF.
           MOVE "first" TO Q-REC. WRITE Q-REC. DISPLAY "qw " QS " " QK.
           MOVE "secnd" TO Q-REC. WRITE Q-REC. DISPLAY "qw " QS " " QK.
           CLOSE QF.
           OPEN I-O RELF. DISPLAY "open " ST.
           MOVE 5 TO RK. MOVE "fifth" TO R-REC. WRITE R-REC.
           DISPLAY "w5 " ST.
           MOVE 2 TO RK. WRITE R-REC INVALID KEY DISPLAY "dup " ST.
           MOVE 0 TO RK. WRITE R-REC INVALID KEY DISPLAY "zero " ST.
           MOVE 3 TO RK. READ RELF INVALID KEY DISPLAY "nf " ST.
           MOVE 9 TO RK. READ RELF INVALID KEY DISPLAY "nf9 " ST.
           MOVE 1 TO RK. READ RELF. DISPLAY "r1 " R-REC " " ST.
           READ RELF NEXT. DISPLAY "next " R-REC " " ST " " RK.
           READ RELF NEXT. DISPLAY "next " R-REC " " ST " " RK.
           READ RELF NEXT AT END DISPLAY "eof " ST.
           READ RELF NEXT. DISPLAY "after eof " ST.
           MOVE 4 TO RK. START RELF KEY < RK. DISPLAY "st lt " ST.
           READ RELF NEXT. DISPLAY "n " R-REC " " ST " " RK.
           MOVE 3 TO RK. START RELF KEY >= RK. DISPLAY "st ge " ST.
           READ RELF NEXT. DISPLAY "n " R-REC " " ST " " RK.
           READ RELF PREVIOUS. DISPLAY "p " R-REC " " ST " " RK.
           READ RELF PREVIOUS. DISPLAY "p " R-REC " " ST " " RK.
           MOVE 2 TO RK. DELETE RELF. DISPLAY "del " ST.
           DELETE RELF. DISPLAY "del again " ST.
           MOVE 7 TO RK. DELETE RELF. DISPLAY "del 7 " ST.
           MOVE 5 TO RK. MOVE "FIFTH" TO R-REC. REWRITE R-REC.
           DISPLAY "rw " ST.
           MOVE 6 TO RK. REWRITE R-REC. DISPLAY "rw 6 " ST.
           MOVE 1 TO RK. START RELF KEY > RK. DISPLAY "st gt " ST.
           READ RELF NEXT. DISPLAY "n " R-REC " " ST " " RK.
           MOVE 6 TO RK. START RELF KEY = RK. DISPLAY "st eq " ST.
           READ RELF NEXT. DISPLAY "after bad start " ST.
           CLOSE RELF.
           OPEN I-O QF.
           READ QF. DISPLAY "q " Q-REC " " QS " " QK.
           MOVE "FIRST" TO Q-REC. REWRITE Q-REC. DISPLAY "qrw " QS.
           READ QF. DISPLAY "q " Q-REC " " QS " " QK.
           DELETE QF. DISPLAY "qdel " QS.
           READ QF. DISPLAY "q " Q-REC " " QS " " QK.
           READ QF. DISPLAY "q " QS.
           CLOSE QF.
           OPEN EXTEND QF. MOVE "extnd" TO Q-REC. WRITE Q-REC.
           DISPLAY "ext " QS " " QK. CLOSE QF.
           OPEN INPUT QF.
           PERFORM 6 TIMES READ QF AT END DISPLAY "e " QS
             NOT AT END DISPLAY "all " Q-REC " " QK END-READ
           END-PERFORM.
           CLOSE QF.
           OPEN OUTPUT NOSF. MOVE "AA" TO N-REC. WRITE N-REC.
           MOVE 0 TO NK. WRITE N-REC INVALID KEY DISPLAY "nk0".
           MOVE 1 TO NK. WRITE N-REC.
           DISPLAY "not reached".
           STOP RUN.
