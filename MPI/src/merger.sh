#!/bin/bash


mpirun.openmpi -np 4 -hostfile ./hostfile -mca btl_base_warn_component_unused=0 --oversubscribe -allow-run-as-root mpi2prv -dump-without-time -v -f TRACE.mpits -e matmul_AdvMpi.o -o TRAZA_MPI.prv

