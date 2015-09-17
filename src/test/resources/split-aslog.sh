#!/bin/bash
# sample script to split monitor logs by space and member
# split-aslog.sh SU_STATE 09_14

IDIR=/opt/asfiles/5/monitor/as_monitor/log
MON=vrh00958-monitor
DT="$2"
ODIR=/opt/asfiles/5/monitor/as_monitor/analysis

if [ "$1" == "SU_STATE" ]; then
# SU_STATE
MS=SU_STATE
SPCS=(PIECE_STATE PIECE_STATE_COMPOSITE Q_PIECE_STATE_ANALYTICS)
MEMS=(suStateRouter1 suStateRouter2 suStateRouter3 suStateRouter4 suStateRouter5)

elif [ "$1" == "SU_DETAIL" ]; then
# SU_DETAIL
MS=SU_DETAIL
SPCS=(PIECE_CUSTOMER PIECE_MOVEMENT_IO_STA PIECE_DELIVERY PIECE_MOVEMENT_INT_STA PIECE_PICKUP)
MEMS=(sefsDetail1 sefsDetail2 sefsDetail3 sefsDetail4 sefsDetail5)

elif [ "$1" == "SU_ADDRESS" ]; then
# SU_ADDRESS
MS=SU_ADDRESS
SPCS=(ADDRESS_IPC ADDRESS PIECE_ADDRESS)
MEMS=(sefsAddress1 sefsAddress2 sefsAddress3 sefsAddress4 sefsAddress5)

else
# SUShipment
MS=SUShipment
SPCS=(Concepts_PieceEvent Concepts_Piece)
MEMS=(SHIPMENT_CACHE_1 SHIPMENT_CACHE_2 SHIPMENT_CACHE_3 SHIPMENT_CACHE_4 SHIPMENT_CACHE_5)
fi

IFILE=${IDIR}/${MS}_${MON}_${DT}.csv

# SPCS=($(awk -F , '{print $2}' $CSV | sort | uniq | egrep -v space_name))
# MEMS=($(awk -F , '{print $4}' $CSV | sort | uniq | egrep -v member_name))

for SPACE in ${SPCS[@]}; do
   for MEMBER in ${MEMS[@]}; do
      echo date_time,space_name,member_name,original_count,replica_name,put_count,take_count,get_count,client_put_count,client_get_count,client_take_count,client_avg_put_micros,client_avg_get_micros,client_avg_take_micros >> ${ODIR}/${MS}_${SPACE}_${MEMBER}.csv
      grep "$SPACE," ${IFILE} | grep $MEMBER | awk -F , -v OFS=',' '{print $1,$2,$4,$6,$7,$8,$9,$10,$19,$20,$21,$27,$30,$33;}' >> ${ODIR}/${MS}_${SPACE}_${MEMBER}.csv
   done
done
