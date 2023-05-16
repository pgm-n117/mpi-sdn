#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <mpi.h>
#include <string.h>


/*
mpirun --np <> --hostfile <> --mca btl tcp,self ./mpi_broadcast_data <filename>
example with file of size 1G and 4 nodes: mpirun --np 4 --hostfile <> --mca btl tcp,self ./mpi_broadcast_data <filename> 

*/

//to create an example file for testing, truncate can be used: truncate -s 1G example.file




int main(int argc, char **argv){ 

    int i;

    static int node;					//task/process id
    static int size;					//total available tasks
    int error;
    static long int sizeoffile;

    static char* filename;
    static char* fullfile;
    double time;


    FILE* fp;


    //Start MPI program
    MPI_Init(&argc, &argv);
    
    MPI_Comm_rank(MPI_COMM_WORLD, &node);

    MPI_Comm_size(MPI_COMM_WORLD, &size);


    if(node == 0){
        size_t filename_size = strlen(argv[1]);

        char * filename = malloc(filename_size);
        strcpy(filename, argv[1]);
        
        // opening the file in read mode
        fp = fopen(filename, "r");
  
        // checking if the file exist or not
        if (fp == NULL) {
            printf("File Not Found!\n");
            return -1;
        }
  
        fseek(fp, 0L, SEEK_END);
        
        // calculating the size of the file
        sizeoffile = ftell(fp);
        

    }

    MPI_Bcast(&sizeoffile, sizeof(sizeoffile), MPI_INT, 0, MPI_COMM_WORLD);
    printf("Size of file: %ld bytes\n", sizeoffile);

    fullfile = (char*)malloc(sizeof(char)*sizeoffile); //Everybody allocates memory

    if(node == 0){
        rewind(fp);
        if(fgets(fullfile, sizeoffile, fp) != NULL){
            printf("File succesfuly read\n");
        }else{

            printf("Error reading file\n");
        }     
    }


    //printf("Broadcasting file to all nodes\n");
    MPI_Barrier(MPI_COMM_WORLD);

    if(node == 0) time -= MPI_Wtime();        //start timer
    
    //Broadcast
    MPI_Bcast(fullfile, sizeoffile, MPI_CHAR, 0, MPI_COMM_WORLD);



    
    MPI_Barrier(MPI_COMM_WORLD);
       
    if(node == 0){
        time += MPI_Wtime();          //end timer
        printf("MPI Execution time: %f\n", time);
  
        //closing the file
        fclose(fp);
        
        
    }

    //printf("Broadcasting finished\n"); 

    free(fullfile);
    if(node == 0) free(filename);


    MPI_Finalize();


}  

