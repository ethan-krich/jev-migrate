       IDENTIFICATION DIVISION.
       PROGRAM-ID. DEVICES.
      * Output devices, the printer and STOP literal.
       ENVIRONMENT DIVISION.
       CONFIGURATION SECTION.
       SPECIAL-NAMES.
           PRINTER IS LISTING
           SYSLST IS SYSLIST-OUT.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT PRINTED ASSIGN TO "print.txt"
               ORGANIZATION IS LINE SEQUENTIAL.
       DATA DIVISION.
       FILE SECTION.
       FD PRINTED.
       01 P-LINE       PIC X(30).
       WORKING-STORAGE SECTION.
       01 WS-REPLY     PIC X(20).
       01 WS-EOF       PIC X VALUE "N".
       PROCEDURE DIVISION.
       MAIN-PARA.
           DISPLAY "to syslist" UPON SYSLIST-OUT.
           DISPLAY "to stderr" UPON STDERR.
           DISPLAY "printed line 1" UPON LISTING.
           DISPLAY "printed " UPON LISTING WITH NO ADVANCING.
           STOP "OPERATOR: MOUNT TAPE".
           ACCEPT WS-REPLY.
           DISPLAY "next input [" WS-REPLY "]".
           OPEN INPUT PRINTED.
           PERFORM UNTIL WS-EOF = "Y"
               READ PRINTED AT END MOVE "Y" TO WS-EOF
                   NOT AT END DISPLAY "print file: " P-LINE
               END-READ
           END-PERFORM.
           CLOSE PRINTED.
           STOP RUN.
