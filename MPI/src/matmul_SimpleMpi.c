#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <mpi.h>

//si no está definido RAND_MAX en stdlib.h, se define como el entero positivo más alto
#ifndef RAND_MAX
#define RAND_MAX ((int) ((unsigned) ~0 >> 1)
#endif

//funciones
double *matrix(int, int);
void print_matrix(double *, int, int);
void fill_matrix(double *, int, int);
void matmul(double *, double *, double *, int, int, int, int);
void save_matrix(double *, int, int, char *);



int main(int argc, char **argv){ 

    int i;

    int node;					//Nº de tarea / proceso
    int size;					//Nº de procesos disponibles
    
    int iA, jA, iB, jB;		//iA, jA: filas x columnas de A, columnas de B
    //Para matrices de tamaños distintos, jA sería igual a iB 
    //i es el número de filas, tamaño de columna
    //j es el número de columnas, *tamaño de filas*:
    //A(i,j) -> A(1,3): fila 1, columna 3

    double time;

    if(argc < 4) {
        printf(" iA: Tamaño de fila de A,\n jA: Tamaño de columna de A,\n jB: Tamaño de columna de B.\n");
        return(0);
    }
    //lectura de parametros de ejecución
    iA = atoi(argv[1]);
    jA = atoi(argv[2]);
    iB = jA;
    jB = atoi(argv[3]);
    
    if (iA == 0 || jA == 0 || jB == 0){
        printf("No puede haber tamaño 0\n");
        exit(-1);
    }
    
    if(iA != jA && jA != iB){
        printf("En el producto de matrices no cuadradas, iA=jB y jA=iB\n");
        exit(-1);
    }


    //Inicio del programa paralelo
    MPI_Init(&argc, &argv);			//Inicio de programa MPI
    
    MPI_Comm_rank(MPI_COMM_WORLD, &node);	//Especifica el comunicador por defecto

    MPI_Comm_size(MPI_COMM_WORLD, &size);


    //Se reserva el espacio para las matrices (nodo maestro)
    //double *mA, *mB, *mC;	//matriz A, B y C (resultado)

    double *mA = matrix(iA, jA);
    double *mB = matrix(iB, jB);
    double *mC = matrix(iA, jB);


    //Creamos el buffer donde recibiremos la submatriz de A
    double *buffmA = matrix((iA/size), jA);
    double *buffmC = matrix((iA/size), jB);

    //Comprobar si la matriz A es divisible por filas entre el número de procesos:
    if(node==0){
        if(iA % size != 0){
            //MPI_Finalize();
            if(node == 0) printf("No se puede repartir la matriz entre el número de nodos\n");
            free(mA);
            free(mB);
            free(mC);
            exit(-1);   
        }
    }
    
    
    //Hacer división de las matrices dependiendo de los procesos
    int buffsize = (iA / size) * jA; //Nº de filas / Nº de procesos * tamaño de fila(Nº de cols)



    //gettimeofday(&start, NULL);		//Para contar el tiempo de ejecucion


    if(node == 0){      //Proceso 0

        
        //Llenamos las matrices
        fill_matrix(mA, iA, jA);
        fill_matrix(mB, iB, jB);

        if(argc > 4){
            if(*argv[4] == 'd'){		//debug
                save_matrix(mA, iA, jA, "mA_mpi.txt");
                save_matrix(mB, iB, jB, "mB_mpi.txt");
                print_matrix(mA, iA, jA);
                print_matrix(mB, iB, jB);
            }
        }


        time -= MPI_Wtime();        //Inicio del contador de tiempo

        //MPI_Send(buffer de salida, tamaño, tipo de datos, destino, etiqueta, comunicador);
        //MPI_Recv(buffer de llegada, tamaño, tipo de datos, origen, etiqueta, comunicador);


        //envío de B a todos
        //envío de submatrices de A

        for(i=1; i<size; i++){
            MPI_Send(mB, iB*jB, MPI_DOUBLE, i, 0, MPI_COMM_WORLD); //Envío de B
            MPI_Send(&mA[buffsize*i], buffsize, MPI_DOUBLE, i, 1, MPI_COMM_WORLD ); //Envío de submatriz de A
        }

        //Computo local
        //Le pasamos solo el rango de la submatriz de A, y se guardará en su correspondiente espacio en C
        matmul(mA, mB, mC, (iA/size), jA, iB, jB); 

        //recepción de resultados en C
        for(i=1; i<size; i++){
            MPI_Recv(&mC[buffsize*i], (iA/size)*jB, MPI_DOUBLE, i, 2, MPI_COMM_WORLD, MPI_STATUS_IGNORE); //Envío de B
        }

    } else {            //Resto de procesos


        MPI_Recv(mB, iB*jB, MPI_DOUBLE, 0, 0, MPI_COMM_WORLD, MPI_STATUS_IGNORE); //Recepción de B
        MPI_Recv(buffmA, buffsize, MPI_DOUBLE, 0, 1, MPI_COMM_WORLD, MPI_STATUS_IGNORE); //Recepción de submatriz de A

        //Computo local
        //Le pasamos solo el rango de la submatriz de A, y se guardará en su correspondiente espacio en C
        matmul(buffmA, mB, buffmC, (iA/size), jA, iB, jB); 

        //Envío de resultado de submatriz de C
        MPI_Send(buffmC, (iA/size)*jB, MPI_DOUBLE, 0, 2, MPI_COMM_WORLD); //Envío de B

    }
    
  
    //gettimeofday(&stop, NULL);		//Dejamos de contar el tiempo de ejecucion

    if(node == 0){
        time += MPI_Wtime();          //Final del contador de tiempo
        save_matrix(mC, iA, jB, "resultado_mpiSimple.txt");
    }
    
    free(buffmA);
    free(buffmC);
    free(mB);

    MPI_Finalize();				//Final de programa MPI

    free(mA);
    free(mC);
    
    
    if(node == 0){
        
        if(argc > 4){
    	    if(*argv[4] == 't'){		//salida de pruebas
                printf("%d x %d, %d x %d: %f\n",iA, jA, iB, jB, time);
            }
        }else printf("Tiempo de ejecución del segmento MPI: %f\n", time);
    
    }
    
}  



double *matrix(int i, int j){
    double *m;
    
    m=(double *)malloc((unsigned)(i)*(j)*sizeof(double));
    if(!m) printf("error de reserva de espacio");
    return m;
}

void fill_matrix(double *m, int i, int j){
    int a,b;
    
    //srand((int) time(NULL));
    
    srand(1); //Semilla fija para hacer pruebas
    
    for(a=0; a<i; a++)
        for(b=0; b<j; b++){
           m[a*j+b] = (double) rand() / (double) RAND_MAX + 1;
        }        

}

void print_matrix(double *m, int i, int j){
    int a,b;
    for(a=0; a<i; a++){
        printf("|");
        for(b=0; b<j; b++){
            printf("%f ", m[a*j+b]);
        }
        printf("|\n");
    }    
    
    printf("\n");    

}

void matmul(double *mA, double *mB, double *mC, int iA, int jA, int iB, int jB){    		
    //MatMul: para comprobar algun resultado: https://matrix.reshish.com/es/multCalculation.php
 
    int i, j, k;
        
    for(i=0; i<iA; i++){					
        for(j=0; j<jB; j++){	
            mC[i*jB+j]=0.0;					//Se inicializa a 0 para evitar errores
            for(k=0; k<jA; k++) 				
                mC[i*jB+j] += mA[i*jA+k] * mB[k*jB+j];
        }
    }        
}

void save_matrix(double *m, int i, int j, char* file){

    int a, b;
    FILE *fd;
    fd = fopen(file, "w"); 	//abrimos fichero en modo escritura (w)
    
    for(a=0; a<i; a++){
        for(b=0; b<j; b++){
           fprintf(fd, "%f ", m[a*j+b]);
        }
        fprintf(fd, "\n");
    }    
    
    fprintf(fd, "\n");    

    fclose(fd);

}


/******Esquema de Wtime:*******

    time -= MPI_Wtime();

    Código a medir;

    time += MPI_Wtime();

    --> En multiples bloques de código

******************************/
