#!/bin/bash
RATE=1000

while [ $# -ge 1 ]; do
        case "$1" in
                --)
                    # No more options left.
                    shift
                    break
                   ;;
                -r|--rate)
                        RATE="$2"
                        shift
                        ;;
                -h)
                        echo "-r|--rate rate per second"
                        exit 0
                        ;;
        esac

        shift
done
echo "sending $RATE messages per second"
echo "POST http://localhost:8080/items/" | vegeta attack -duration=5s -body=body.json -rate $RATE -header='Content-Type: application/json' | tee results.bin | vegeta report
