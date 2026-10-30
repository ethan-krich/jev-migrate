       IDENTIFICATION DIVISION.
       PROGRAM-ID. SORTFILE.
      * SORT / MERGE with COLLATING SEQUENCE, several GIVING files,
      * RELATIVE and INDEXED USING / GIVING files.
       ENVIRONMENT DIVISION.
       CONFIGURATION SECTION.
       SPECIAL-NAMES.
           ALPHABET MAINFRAME IS EBCDIC
           ALPHABET LOCAL IS NATIVE.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT NAMES ASSIGN TO "names.txt"
               ORGANIZATION IS LINE SEQUENTIAL.
           SELECT OUT1 ASSIGN TO "out1.txt"
               ORGANIZATION IS LINE SEQUENTIAL.
           SELECT OUT2 ASSIGN TO "out2.txt"
               ORGANIZATION IS LINE SEQUENTIAL.
           SELECT SEQ-F ASSIGN TO "seq.dat"
               ORGANIZATION IS SEQUENTIAL.
           SELECT REL-F ASSIGN TO "rel.dat"
               ORGANIZATION IS RELATIVE ACCESS IS SEQUENTIAL.
           SELECT IDX-F ASSIGN TO "idx.dat"
               ORGANIZATION IS INDEXED ACCESS IS DYNAMIC
               RECORD KEY IS I-KEY.
           SELECT IDX2-F ASSIGN TO "idx2.dat"
               ORGANIZATION IS INDEXED ACCESS IS SEQUENTIAL
               RECORD KEY IS I2-KEY.
           SELECT WORK ASSIGN TO "work.tmp".
       DATA DIVISION.
       FILE SECTION.
       FD NAMES.
       01 N-REC        PIC X(10).
       FD OUT1.
       01 O1-REC       PIC X(10).
       FD OUT2.
       01 O2-REC       PIC X(10).
       FD SEQ-F.
       01 SQ-REC       PIC X(10).
       FD REL-F.
       01 R-REC        PIC X(10).
       FD IDX-F.
       01 I-REC.
          05 I-KEY     PIC X(7).
          05 I-REST    PIC X(3).
       FD IDX2-F.
       01 I2-REC.
          05 I2-REST   PIC X(7).
          05 I2-KEY    PIC X(3).
       SD WORK.
       01 W-REC.
          05 W-NAME    PIC X(7).
          05 W-NUM     PIC 99.
          05 FILLER    PIC X.
       WORKING-STORAGE SECTION.
       01 EOF          PIC X.
       PROCEDURE DIVISION.
       MAIN-PARA.
           SORT WORK ON ASCENDING KEY W-NAME
               COLLATING SEQUENCE IS MAINFRAME
               USING NAMES GIVING OUT1 OUT2.
           DISPLAY "ebcdic order:".
           PERFORM SHOW-OUT1.
           OPEN INPUT OUT2.
           READ OUT2 AT END DISPLAY "out2 empty" END-READ.
           DISPLAY "out2 first " O2-REC.
           CLOSE OUT2.
           SORT WORK ON ASCENDING KEY W-NAME
               COLLATING SEQUENCE LOCAL
               USING NAMES GIVING REL-F SEQ-F.
           DISPLAY "native order (relative):".
           OPEN INPUT REL-F.
           MOVE "N" TO EOF.
           PERFORM UNTIL EOF = "Y"
               READ REL-F NEXT AT END MOVE "Y" TO EOF
                   NOT AT END DISPLAY "  " R-REC
               END-READ
           END-PERFORM.
           CLOSE REL-F.
           OPEN OUTPUT IDX-F.
           MOVE "mike   003" TO I-REC. WRITE I-REC.
           MOVE "able   001" TO I-REC. WRITE I-REC.
           MOVE "zed    002" TO I-REC. WRITE I-REC.
           CLOSE IDX-F.
           SORT WORK ON DESCENDING KEY W-NUM
               USING IDX-F REL-F GIVING IDX2-F OUT1.
           DISPLAY "by number, indexed out:".
           OPEN INPUT IDX2-F.
           MOVE "N" TO EOF.
           PERFORM UNTIL EOF = "Y"
               READ IDX2-F NEXT AT END MOVE "Y" TO EOF
                   NOT AT END DISPLAY "  " I2-REC
               END-READ
           END-PERFORM.
           CLOSE IDX2-F.
           PERFORM SHOW-OUT1.
           MERGE WORK ON ASCENDING KEY W-NAME
               COLLATING SEQUENCE MAINFRAME
               USING OUT2 SEQ-F GIVING OUT1.
           DISPLAY "merged:".
           PERFORM SHOW-OUT1.
           STOP RUN.
       SHOW-OUT1.
           OPEN INPUT OUT1.
           MOVE "N" TO EOF.
           PERFORM UNTIL EOF = "Y"
               READ OUT1 AT END MOVE "Y" TO EOF
                   NOT AT END DISPLAY "  " O1-REC
               END-READ
           END-PERFORM.
           CLOSE OUT1.
