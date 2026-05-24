#!/bin/bash
# Run Besu on ETC mainnet (chain ID 61)
# Ports offset from core-geth (8545/8546/30303) to allow concurrent running

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

exec "$SCRIPT_DIR/build/install/besu/bin/besu" \
  --genesis-file="$SCRIPT_DIR/config/classic.json" \
  --data-path=/media/dev/2tb/data/blockchain/besu/classic \
  --network-id=1 \
  --rpc-http-enabled \
  --rpc-http-host=0.0.0.0 \
  --rpc-http-port=8548 \
  --rpc-http-cors-origins="*" \
  --rpc-http-api=ETH,NET,WEB3,DEBUG,TXPOOL \
  --rpc-ws-enabled \
  --rpc-ws-host=0.0.0.0 \
  --rpc-ws-port=8549 \
  --p2p-port=30304 \
  --data-storage-format=BONSAI \
  --sync-mode=FULL \
  --logging=INFO \
  "$@"
