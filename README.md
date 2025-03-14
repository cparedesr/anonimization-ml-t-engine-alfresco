# anonimization-ml-t-engine-alfresco
Anonimizar documentos a través de un t engine basado en ML spacy.

Spacy es un modelo bastante liviano pero eficaz de Machine Learning específico para el anonimizado de documentos. Por lo que hay que tener en consideración las restricciones de memoria del propio servicio.

Pasos para levatnar el proyecto:
```
git clone https://github.com/cparedesr/anonimization-ml-t-engine-alfresco.git
cd anonimization-ml-t-engine-alfresco
docker compose up --build
```

La transformación del propio documento se hace mediante una regla de contenido:
```
Cuando: Se crean elementos o ingresan a esta carpeta
Si se cumplen todos los criterios: El tipo MIME es 'Documento Adobe PDF'
Realizar acción: Incrustar propiedades como metadatos en el contenido
```

Esta es la primera versión del transformador que se centra en los datos personales como DNI, Dirección, correo electrónico... Aunque spacy es altamente configurable y se le pueden añadir más entidades para que anonimize a corde con nuestras necesidades.

El documento original se mantiene en la versión 1.0 mientras que para el nuevo documento anonimizado se crea una nueva versión 1.1.

A continuación podemos ver un pequeño ejemplo:


![Documento anonimizado](images/example.png)