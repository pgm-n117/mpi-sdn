#!/bin/bash
for i in {1..10};
do
    $(mpirun.openmpi -np 96 -hostfile ./hostfile -mca btl_base_warn_component_unused=0 --oversubscribe --allow-run-as-root ./matmul_AdvMpi.o $(($i * 96)) $(($i * 96)) $(($i * 96)) t >> advResults.txt) 

done

for i in {1..10};
do
    $(mpirun.openmpi -np 96 -hostfile ./hostfile -mca btl_base_warn_component_unused=0 --oversubscribe --allow-run-as-root ./matmul_SimpleMpi.o $(($i * 96)) $(($i * 96)) $(($i * 96)) t >> SimpleResults.txt) 

done