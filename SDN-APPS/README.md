# SDN APPS
This directory contains the ONOS developed apps, and the Ryu docker files for launching Ryu controller on your own machine. Ryu developed apps are not included in this repo yet.
## ONOS
### mpi-controller
Forwarding app that implements parallel links forwarding. Works with an OpenMPI modified version that notifies every mpi listener process to the controller using UDP messages at mpi_init time, and installs path flowrules on every switch at the topology before mpi communications starts using all available links, for maximizing overall bandwidth and link usage.
- New: OpenMPI UDP notification updated. Now mpi_finalize is also notified, so the controller can remove the flowrules installed for the mpi communications.

### proactive-switch-app
Proactive forwarding app that installs flowrules for every IP traffic detected on the topology. Simple forwarding app.

### tutorial-learning-switch
ONOS tutorial learning switch

## RYU
More information on the Ryu directory