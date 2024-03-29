#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <mpi.h>
#include <string.h>


/*
mpirun --np <> --hostfile <> --mca btl tcp,self ./mpi_broadcast_data <filename> <size>
example with file of size 1G and 4 nodes: mpirun --np 4 --hostfile <> --mca btl tcp,self ./mpi_broadcast_data <filename> 1000000 

*/

//to create an example file for testing, truncate can be used: truncate -s 1G example.file




int main(int argc, char **argv){ 

    int i;

    int node;					//Nº de tarea / proceso
    int size;					//Nº de procesos disponibles
    int error;
    long int res; 
    
    int sizeoffile = atoi(argv[2]);
    char* fullfile;
    double time;

    size_t filename_size = strlen(argv[1]);
    char * filename = malloc(filename_size);
    strcpy(filename, argv[1]);

    FILE* fp;


    //Inicio del programa paralelo
    MPI_Init(&argc, &argv);			//Inicio de programa MPI
    
    MPI_Comm_rank(MPI_COMM_WORLD, &node);	//Especifica el comunicador por defecto

    MPI_Comm_size(MPI_COMM_WORLD, &size);


    if(node == 0){
        
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
            printf("Error al leer el fichero");

        }
    }

    fullfile = (char*)malloc(sizeof(char)*sizeoffile); //Everybody allocates memory

    if(node == 0){
        rewind(fp);
        if(fgets(fullfile, res, fp) != NULL){
            printf("Fichero leido correctamente\n");
        }else{

            printf("Fichero no leido correctamente\n");
        } 
        
        
        
        
    }

    MPI_Barrier(MPI_COMM_WORLD);

    if(node == 0) time -= MPI_Wtime();        //Inicio del contador de tiempo
    //Broadcast
    MPI_Bcast(fullfile, sizeoffile, MPI_CHAR, 0, MPI_COMM_WORLD);



    MPI_Barrier(MPI_COMM_WORLD);
    if(node == 0){
        time += MPI_Wtime();          //Final del contador de tiempo

          
        // closing the file
        fclose(fp);
        
        printf("Tiempo de ejecución del segmento MPI: %f\n", time);
    }
    free(fullfile);
    free(filename);

    MPI_Finalize();				//Final de programa MPI

}  

