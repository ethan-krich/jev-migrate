       IDENTIFICATION DIVISION.
       PROGRAM-ID. IDXFILES.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT KFILE ASSIGN TO "keyed.dat"
               ORGANIZATION IS INDEXED
               ACCESS MODE IS DYNAMIC
               RECORD KEY IS K-ID
               ALTERNATE RECORD KEY IS K-CITY WITH DUPLICATES
               FILE STATUS IS ST.
           SELECT SFILE ASSIGN TO "seq.dat"
               ORGANIZATION IS INDEXED
               ACCESS MODE IS SEQUENTIAL
               RECORD KEY IS S-ID
               FILE STATUS IS SS.
           SELECT NFILE ASSIGN TO "nost.dat"
               ORGANIZATION IS INDEXED
               ACCESS MODE IS RANDOM
               RECORD KEY IS N-K.
       DATA DIVISION.
       FILE SECTION.
       FD KFILE.
       01 K-REC.
          05 K-ID    PIC X(4).
          05 K-CITY  PIC X(6).
          05 K-AMT   PIC 9(3).
       FD SFILE.
       01 S-REC.
          05 S-ID    PIC 9(3).
          05 S-TXT   PIC X(5).
       FD NFILE.
       01 N-REC.
          05 N-K  PIC X(2).
          05 N-V  PIC X(3).
       WORKING-STORAGE SECTION.
       01 ST PIC XX.
       01 SS PIC XX.
       PROCEDURE DIVISION.
           OPEN INPUT KFILE. DISPLAY "open missing " ST.
           OPEN I-O KFILE. DISPLAY "open io missing " ST.
           OPEN OUTPUT KFILE. DISPLAY "open out " ST.
           MOVE "B002PARIS 010" TO K-REC. WRITE K-REC
             INVALID KEY DISPLAY "inv" END-WRITE. DISPLAY "w1 " ST.
           MOVE "A001ROME  020" TO K-REC. WRITE K-REC. DISPLAY "w2 " ST.
           MOVE "C003PARIS 030" TO K-REC. WRITE K-REC. DISPLAY "w3 " ST.
           MOVE "A001OSLO  040" TO K-REC. WRITE K-REC
             INVALID KEY DISPLAY "dup " ST END-WRITE.
           MOVE "D004PARIS 050" TO K-REC. WRITE K-REC. DISPLAY "w5 " ST.
           READ KFILE. DISPLAY "read on output " ST.
           CLOSE KFILE. DISPLAY "close " ST.
           OPEN I-O KFILE. DISPLAY "open io " ST.
           MOVE "C003" TO K-ID. READ KFILE
             INVALID KEY DISPLAY "nf"
             NOT INVALID KEY DISPLAY "got " K-REC " " ST END-READ.
           MOVE "Z999" TO K-ID. READ KFILE
             INVALID KEY DISPLAY "nf " ST " " K-REC END-READ.
           READ KFILE NEXT AT END DISPLAY "eof " ST
             NOT AT END DISPLAY "next after nf " K-REC " " ST.
           MOVE "B000" TO K-ID.
           START KFILE KEY >= K-ID INVALID KEY DISPLAY "st inv".
           DISPLAY "start ge " ST " " K-REC.
           PERFORM 6 TIMES
             READ KFILE NEXT AT END DISPLAY "eof " ST
               NOT AT END DISPLAY "seq " K-REC " " ST END-READ
           END-PERFORM.
           MOVE "PARIS " TO K-CITY.
           START KFILE KEY = K-CITY. DISPLAY "start city " ST.
           PERFORM 4 TIMES
             READ KFILE NEXT AT END DISPLAY "eof " ST
               NOT AT END DISPLAY "city " K-REC " " ST END-READ
           END-PERFORM.
           MOVE "PARIS " TO K-CITY.
           READ KFILE KEY IS K-CITY. DISPLAY "rk city " K-REC " " ST.
           MOVE "B002" TO K-ID. READ KFILE. DISPLAY "r " K-REC " " ST.
           MOVE "LYON  " TO K-CITY. REWRITE K-REC. DISPLAY "rw " ST.
           MOVE "X009" TO K-ID. REWRITE K-REC
             INVALID KEY DISPLAY "rw inv " ST END-REWRITE.
           MOVE "C003" TO K-ID. DELETE KFILE RECORD. DISPLAY "del " ST.
           DELETE KFILE INVALID KEY DISPLAY "del again " ST END-DELETE.
           MOVE "ZZZZ" TO K-ID. START KFILE KEY > K-ID
             INVALID KEY DISPLAY "start inv " ST END-START.
           READ KFILE NEXT AT END DISPLAY "after bad start " ST.
           DISPLAY "after bad start " ST.
           MOVE LOW-VALUES TO K-ID. START KFILE KEY NOT < K-ID.
           DISPLAY "start lo " ST.
           PERFORM 5 TIMES
             READ KFILE NEXT AT END DISPLAY "eof " ST
               NOT AT END DISPLAY "all " K-REC " " ST END-READ
           END-PERFORM.
           READ KFILE NEXT AT END DISPLAY "eof2 " ST.
           MOVE "D004" TO K-ID. START KFILE KEY <= K-ID.
           DISPLAY "start le " ST.
           READ KFILE PREVIOUS AT END DISPLAY "peof " ST
             NOT AT END DISPLAY "prev " K-REC " " ST END-READ.
           READ KFILE PREVIOUS AT END DISPLAY "peof " ST
             NOT AT END DISPLAY "prev " K-REC " " ST END-READ.
           CLOSE KFILE.
           OPEN OUTPUT SFILE.
           MOVE 5 TO S-ID. MOVE "five" TO S-TXT. WRITE S-REC.
           DISPLAY "sw " SS.
           MOVE 3 TO S-ID. MOVE "three" TO S-TXT. WRITE S-REC.
           DISPLAY "sw out of order " SS.
           MOVE 5 TO S-ID. WRITE S-REC. DISPLAY "sw dup " SS.
           MOVE 9 TO S-ID. MOVE "nine" TO S-TXT. WRITE S-REC.
           CLOSE SFILE.
           OPEN I-O SFILE.
           REWRITE S-REC. DISPLAY "rw no read " SS.
           READ SFILE. DISPLAY "sr " S-REC " " SS.
           MOVE 6 TO S-ID. REWRITE S-REC. DISPLAY "rw key chg " SS.
           READ SFILE. DISPLAY "sr " S-REC " " SS.
           MOVE "NINE" TO S-TXT. REWRITE S-REC. DISPLAY "rw " SS.
           DELETE SFILE. DISPLAY "sdel " SS.
           DELETE SFILE. DISPLAY "sdel again " SS.
           READ SFILE. DISPLAY "sr end " SS.
           READ SFILE. DISPLAY "sr end2 " SS.
           CLOSE SFILE.
           OPEN EXTEND SFILE. DISPLAY "ext " SS.
           MOVE 2 TO S-ID. WRITE S-REC. DISPLAY "ext low " SS.
           MOVE 7 TO S-ID. WRITE S-REC. DISPLAY "ext hi " SS.
           CLOSE SFILE.
           OPEN INPUT SFILE.
           PERFORM 4 TIMES READ SFILE AT END DISPLAY "e " SS
             NOT AT END DISPLAY "fin " S-REC END-READ END-PERFORM.
           CLOSE SFILE.
           OPEN OUTPUT NFILE. MOVE "AAone" TO N-REC. WRITE N-REC.
           MOVE "AA" TO N-K. READ NFILE. DISPLAY "n " N-REC.
           WRITE N-REC.
           DISPLAY "not reached".
           STOP RUN.
