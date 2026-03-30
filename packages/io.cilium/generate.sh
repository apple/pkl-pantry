#!/bin/bash
set -euo pipefail

BASE_URL="https://raw.githubusercontent.com/cilium/cilium/refs/heads/main/pkg/k8s/apis/cilium.io/client/crds"
GENERATE_PKG="package://pkg.pkl-lang.org/pkl-pantry/k8s.contrib.crd@4.2.1#/generate.pkl"

V2_CRDS=(
  ciliumbgpadvertisements.yaml
  ciliumbgpclusterconfigs.yaml
  ciliumbgpnodeconfigoverrides.yaml
  ciliumbgpnodeconfigs.yaml
  ciliumbgppeerconfigs.yaml
  ciliumcidrgroups.yaml
  ciliumclusterwideenvoyconfigs.yaml
  ciliumclusterwidenetworkpolicies.yaml
  ciliumegressgatewaypolicies.yaml
  ciliumendpoints.yaml
  ciliumenvoyconfigs.yaml
  ciliumidentities.yaml
  ciliumloadbalancerippools.yaml
  ciliumlocalredirectpolicies.yaml
  ciliumnetworkpolicies.yaml
  ciliumnodeconfigs.yaml
  ciliumnodes.yaml
)

V2ALPHA1_CRDS=(
  ciliumendpointslices.yaml
  ciliumgatewayclassconfigs.yaml
  ciliuml2announcementpolicies.yaml
  ciliumpodippools.yaml
)

for crd in "${V2_CRDS[@]}"; do
  echo "Generating v2/$crd..."
  pkl eval "$GENERATE_PKG" -m . -p source="$BASE_URL/v2/$crd" -p baseApiGroup="cilium.io"
done

for crd in "${V2ALPHA1_CRDS[@]}"; do
  echo "Generating v2alpha1/$crd..."
  pkl eval "$GENERATE_PKG" -m . -p source="$BASE_URL/v2alpha1/$crd" -p baseApiGroup="cilium.io"
done

echo "Done!"
