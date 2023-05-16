#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <string.h>
#include <mpi.h>



/*
mpirun --np <> --hostfile <> --mca btl tcp,self ./mpi_send_data <filename>
example with file of size 1G and 4 nodes: mpirun --np 4 --hostfile <> --mca btl tcp,self ./mpi_send_data <filename>

*/

//to create an example file for testing, truncate can be used: truncate -s 1G example.file



int main(int argc, char **argv){ 

    static int node;					//task/process id
    static int size;					//total available tasks
    int error;
    
    static char* fullfile;
    static char* recvfile;
    
    MPI_Status status;
    
    FILE* fp;
    double time;
    
    
    size_t filename_size = strlen(argv[1]);
    char * filename = malloc(sizeof(char)*filename_size+1);
    strcpy(filename, argv[1]);




    MPI_Init(&argc, &argv);
    
    MPI_Comm_rank(MPI_COMM_WORLD, &node);

    MPI_Comm_size(MPI_COMM_WORLD, &size);
    
    

    //First half of the nodes
    if(node < size/2){
    	long int size_s;
    	int remote_node = node+(size/2);
    	
    
    	printf("Node %d\n", node);
        
        // opening the file in read mode
        fp = fopen(filename, "r");
  
        // checking if the file exist or not
        if (fp == NULL) {
            printf("File Not Found!\n");
            return -1;
        }
  
        fseek(fp, 0L, SEEK_END);
        
        // calculating the size of the file
        size_s = ftell(fp);
        //printf("Size of file: %ld bytes\n", size_s);

        //Send size of data to second half of nodes
        MPI_Send(&size_s, sizeof(size_s), MPI_LONG, remote_node, 0, MPI_COMM_WORLD);



        fullfile = (char*)malloc(sizeof(char)*size_s);
        rewind(fp);
        if(fgets(fullfile, size_s, fp) != NULL){
            printf("File succesfuly read on process %d\n", node);
        }else{

            printf("Error 2 reading file\n");
        } 

        // closing the file
        rewind(fp);
        fclose(fp);
        
        printf("Sending data from process %d to process %d\n", node, remote_node);

        if(node == 0){
            time -= MPI_Wtime();        //Timer
        }
        
        
        //send to second half of nodes
        MPI_Send(fullfile, size_s, MPI_CHAR, remote_node, 0, MPI_COMM_WORLD);
        

    }
    

    
    if(node >= size/2){

        int result;
    	long int size_r;	
    	static int remote_node; 
        remote_node = node-(size/2);


    	
        //Receive size of file
        MPI_Recv(&size_r, sizeof(size_r), MPI_LONG, remote_node, 0, MPI_COMM_WORLD, &status);

        //printf("Recv size status: %ld\n", status._ucount/sizeof(long int));


        recvfile = (char*)malloc(sizeof(char)*size_r);
        
        
        //if(recvfile == NULL) printf("ERROR ALLOCATING MEMORY ON RECEIVER");

        //printf("remote node %d\n", remote_node);
        result = MPI_Recv(recvfile, size_r, MPI_CHAR, remote_node, 0, MPI_COMM_WORLD, &status);
        
        //printf("Recv file status: %ld\n", status._ucount);
        //if(result == MPI_SUCCESS){
        //    printf("Received data on process %d from process %d\n", remote_node, node);
        //}
    }
    
    MPI_Barrier(MPI_COMM_WORLD);



    if(node == 0){
        time += MPI_Wtime();          //End timer
        printf("MPI Execution time: %f\n", time);
    }

    if(node < size/2){
        
        free(fullfile);
        //printf("Free memory on process %d\n", node);
    }else{
        free(recvfile);
        //printf("Free memory on process %d\n", node);
    }

    //free(filename);
    
    MPI_Finalize();

}  


