#!/bin/bash
target='default'
numberOfRuns=10

while [ $# -ge 1 ]; do
        case "$1" in
                --)
                    # No more options left.
                    shift
                    break
                   ;;
                -t|--target)
                        target="$2"
                        shift
                        ;;
                -n|--numberOfRuns)
                        numberOfRuns=$2
                        shift
                        ;;
                -h)
                        echo "-t|--target name of target"
                        exit 0
                        ;;
        esac

        shift
done
mkdir -p $target
for i in $(seq 1 $numberOfRuns);
do
  echo "[$i] executing run $i"
  rates=(100 250 500 1000 5000 10000 20000 50000)
  for rate in "${rates[@]}"
  do
    echo "[$i] Running with $rate requests/s"
    ./create-attack.sh -r $rate > $target/$rate-$i.txt
    echo "$(cat $target/$rate-$i.txt)"
    echo "Done!"
  done
done
