#cat $1| awk -F" " '{print "echo '\''val pc1objval = "$2";val pc2objval = "$3";val objnum = "$1"'\'' > calib"$4$1".oms ; tail -n +2 calibration"$4".oms >> calib"$4$1".oms ; openmole --script calib"$4$1".oms --password-file omlpsswd --mem 120G;mkdir calibration/`ls -t calib | head -n 1`; cp calib"$4$1".oms calibration/`ls -t calib | head -n 1`"}' | sh
cat $1| awk -F" " '{print "echo '\''val pc1objval = "$2";val pc2objval = "$3";val objnum = "$1"'\'' > calib"$4$1".oms ; tail -n +2 calibration"$4".oms >> calib"$4$1".oms"}' | sh