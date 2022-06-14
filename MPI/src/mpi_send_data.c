#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <string.h>
#include <mpi.h>



/*
mpirun --np <> --hostfile <> --mca btl tcp,self ./mpi_send_data <filename> <size>
example with file of size 1G and 4 nodes: mpirun --np 4 --hostfile <> --mca btl tcp,self ./mpi_send_data <filename> 1000000 

*/

//to create an example file for testing, truncate can be used: truncate -s 1G example.file



int main(int argc, char **argv){ 

    int i;

    int node;					//#task
    int size;					//total available tasks
    int error;
    long int res; 
    
    int sizeoffile = atoi(argv[2]);
    FILE* fp;
    double time;
    char* fullfile;
    char* recvfile;
    size_t filename_size = strlen(argv[1]);
    char * filename = malloc(filename_size);
    strcpy(filename, argv[1]);




    MPI_Init(&argc, &argv);
    
    MPI_Comm_rank(MPI_COMM_WORLD, &node);

    MPI_Comm_size(MPI_COMM_WORLD, &size);

    //First half of the nodes
    if(node < size/2){
        
        // opening the file in read mode
        fp = fopen(filename, "r");
  
        // checking if the file exist or not
        if (fp == NULL) {
            printf("File Not Found!\n");
            return -1;
        }
  
        fseek(fp, 0L, SEEK_END);
        
        // calculating the size of the file
        res = ftell(fp);
        if(res != sizeoffile){
            printf("Error 1 reading file");

        }

        fullfile = (char*)malloc(sizeof(char)*res);
        rewind(fp);
        if(fgets(fullfile, res, fp) != NULL){
            printf("Fichero leido correctamente\n");
        }else{

            printf("Error 2 reading file\n");
        } 
        
        if(node == 0){
            time -= MPI_Wtime();        //Timer
        }

        //sned to second half of nodes
        MPI_Send(fullfile, sizeoffile, MPI_CHAR, node+(size/2), 0, MPI_COMM_WORLD);


    }else{
        recvfile = (char*)malloc(sizeof(char)*sizeoffile);

        if(MPI_Recv(recvfile, sizeoffile, MPI_CHAR, node-(size/2), 0, MPI_COMM_WORLD, MPI_STATUS_IGNORE) == MPI_SUCCESS){
            printf("Datos recibidos en el nodo %d\n", node);
        }

        

    }

    MPI_Barrier(MPI_COMM_WORLD);

    if(node == 0){
        time += MPI_Wtime();          //End timer
        printf("Tiempo de ejecución del segmento MPI: %f\n", time);
                // closing the file
        fclose(fp);

        //printf("Fichero abierto en proceso %d, tamaño: %ld\n", node, res);
        free(fullfile);
    }else{
        free(recvfile);
    }

    free(filename);

    MPI_Finalize();				//Final de programa MPI

}  


