# ESUP File Manager

> Esup File Manager allows users to perform file management on their home directories.

## Features

* full ajax (jquery)
* WAI User Interface
* mobile look for browsing files (JQuery Mobile)
* copy/cut/past, rename, create folder, upload/download files
* copy/cut/past inter-servers
* use apache commons vfs -> file systems supported are here : http://commons.apache.org/vfs/filesystems.html - uri like file:///home/bob works for example - ftp and sftp ok ...
* CIFS support (with JCIFS-NG)
* Webdav support (with Sardine)
~~* use spring v3, mvc, annotations, etc.~~
* allow to configure servers access with a configuration file

## This version of esup-filemanager is now a servlet application that is cassified

This version is a fork of the original esup-filemanager project. 

It is now a servlet application and no more a portlet application.

## Configuration

You have to configure your application with CAS parameters in application.properties file.

drives.xml let you configure the drives you want to access.

This project requires Java 21 and Maven to build.

## Package

To have the war file, you have to run the maven command : 

```
mvn clean package
```

## Run

If you want to launch directly the application, you can run the command : 

```
mvn spring-boot:run
```

Next go to http://localhost:8080
