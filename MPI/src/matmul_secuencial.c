#include <stdio.h>
#include <stdlib.h>
#include <time.h>
//#include <mpi.h>

//si no está definido RAND_MAX en stdlib.h, se define como el entero positivo más alto
#ifndef RAND_MAX
#define RAND_MAX ((int) ((unsigned) ~0 >> 1)
#endif

//funcion para la reserva de espacio en memoria para matrices
double *matrix(int, int);
void print_matrix(double *, int, int);
void fill_matrix(double *, int, int);
void matmul(double *, double *, double *, int, int, int, int);
void save_matrix(double *, int, int, char *);



int main(int argc, char **argv){ 


    int iA, jA, iB, jB;		//iA, jA: filas x columnas de A, columnas de B
    //Para matrices de tamaños distintos, jA sería igual a iB 
    //i es el número de filas, tamaño de columna
    //j es el número de columnas, *tamaño de filas*:
    //A(i,j) -> A(1,3): fila 1, columna 3
    
    
    struct timeval start, stop;
    double time;
    
    

    if(argc < 4) {
        printf(" iA: Tamaño de fila de A,\n jA: Tamaño de columna de A,\n jB: Tamaño de columna de B.\n");
        exit(-1);
    }
    //lectura de parametros de ejecución
    iA = atoi(argv[1]);
    jA = atoi(argv[2]);
    iB = jA;
    jB = atoi(argv[3]);
    
    
    
    if(iA != jA && jA != iB){
        printf("En el producto de matrices no cuadradas, iA=jB y jA=iB");
        exit(-1);
    }
    
    //double *mA, *mB, *mC;	//matriz A, B y C (resultado)

    double *mA = matrix(iA, jA);
    double *mB = matrix(iB, jB);
    double *mC = matrix(iA, jB);


    gettimeofday(&start, NULL);

    //Llenamos las matrices
    fill_matrix(mA, iA, jA);
    fill_matrix(mB, iB, jB);
    
    if(argc > 4){
    	if(*argv[4] == 'd'){		//debug
    	    save_matrix(mA, iA, jA, "mA.txt");
    	    save_matrix(mB, iB, jB, "mB.txt");
    	    print_matrix(mA, iA, jA);
    	    print_matrix(mB, iB, jB);
    	}
    }

   matmul(mA, mB, mC, iA, jA, iB, jB);

   gettimeofday(&stop, NULL);

   if(argc > 4){
    	if(*argv[4] == 'd'){		//debug
    	    print_matrix(mC, iA, jB);
    	}
    }
   save_matrix(mC, iA, jB, "resultado_sec.txt");
   
   free(mA);
   free(mB);
   free(mC);

    
   time = (stop.tv_sec + stop.tv_usec * 1e-6) - (start.tv_sec + start.tv_usec * 1e-6);
   printf("Tiempo de ejecución: %f\n", time);

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
            mC[i*jB+j] = 0;					
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
