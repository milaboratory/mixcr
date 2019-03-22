grammar Test;

@members {
int count = 0;
}

list
@after {System.out.println(count+" ints");}
: INT {count++;} (',' INT {count++;} )*
;

INT : [0-9]+ ;
WS : [ \r\t\n]+ -> skip ;