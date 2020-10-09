#!/bin/bash
# @ job_name = traza_matmul
# @ output = traza_matmul.out
# @ initialdir = .
# @ total_tasks = 4
# @ cpus_per_task = 1
# @ wall_clock_limit = 00:10:00


$*

mpirun.openmpi -np 4 -hostfile ./hostfile -mca btl_base_warn_component_unused=0 --oversubscribe -allow-run-as-root ./extrae_script.sh ./matmul_SimpleMpi.o 16 16 16
