       IDENTIFICATION DIVISION.
       PROGRAM-ID. RETSUB.
      * Returns twice its argument, negated above 100, in RETURN-CODE.
       DATA DIVISION.
       LINKAGE SECTION.
       01 LS-ARG     PIC 9(3).
       PROCEDURE DIVISION USING LS-ARG.
           COMPUTE RETURN-CODE = LS-ARG * 2.
           IF LS-ARG > 100
               COMPUTE RETURN-CODE = 0 - RETURN-CODE
           END-IF.
           GOBACK.
