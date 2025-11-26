Import("env")

# Change output firmware name from firmware.bin to RelayKVM.bin
env.Replace(PROGNAME="RelayKVM")
