# PostgreSQL-JDBC-Party-Event-App-Test-Database
This is a PostgreSQL JDBC Database that I made in June 2022 as part of university coursework. The program populates, modifies and queries a mock postgreSQL database 
surrounding an Android party/event app that I have been working on and will be releasing eventually.

## Usage
To use this Databse download and open the "PostgreSQL Test Database" folder as a project in IntelliJ IDEA. The JDBC PostgreSQL Java code ("DatabaseTest.java") 
will be in the src folder. In the src folder there is also the postgresql-42.5.1.jar Postgresql driver. You might need to add and install the driver to the project
by going to "File -> Project Structure -> Libraries". There you can add the postgresql-42.5.1.jar and click apply. Before you can begin using the project, you 
will have to create a new empty Database using the postgreSQL pgAdmin 4 program. The databse and password can be whatever you like. If you dont want to change the
name and password in the code, simply name the new Database "DatabaseTestDatabase" and make it have the password "DatabaseTestPassword". The username should be the
default username "postgres". Now you should be all set up and can start using the database.
