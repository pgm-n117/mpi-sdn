#!/bin/bash


mpirun.openmpi -np 16 -hostfile ~/mpi-sdn/MPI/src/hostfile -mca btl_base_warn_component_unused=0 --oversubscribe -allow-run-as-root mpimpi2prv -dump-without-time -v -f  TRACE.mpits -e ~/mpi-sdn/MPI/src/matmul_SimpleMpi_DEBUG.o

