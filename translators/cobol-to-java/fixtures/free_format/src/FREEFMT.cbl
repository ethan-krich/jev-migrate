>>SOURCE FORMAT IS FREE
*> Free-format source: no sequence area, no indicator column.
identification division.
program-id. FREEFMT.
data division.
working-storage section.
01 ws-text      pic x(40) value "a *> inside a literal is not a comment".
01 ws-quote     pic x(12) value 'it''s "fine"'.
01 ws-n         pic 9(3) value 7.
procedure division.
main-para.
    display ws-text.  *> trailing comment
    display ws-quote
  display "starts in column 3"
display "column one"
                                                                                display "far right, past column 72: " ws-n
>>D display "debugging line, not compiled"
    *> an indented comment line
    add 1 to ws-n *> comment after code
    display "n " ws-n
    stop run.
