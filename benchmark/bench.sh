#!/bin/zsh -p
# exec ./echo-benchmark -t64 -c10 -e10000 -p 7778 -f bench-input-file 127.0.0.1
exec ./echo-benchmark -t100 -c10 -e3000 -p 7777 -f bench-input-file 127.0.0.1
# exec ./echo-benchmark -t20 -c10 -e300 -p 7777 -f bench-input-file 127.0.0.1

