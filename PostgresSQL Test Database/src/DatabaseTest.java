import java.sql.*;
import java.util.ArrayList;
import java.util.List;
// The following Postgresql JDBC database is used to store data concerning the Evenue App Prototype created for a course at the University of Regensburg
// called "Anwendungsprogrammierung", where the Development of mobile android applications is taught.
// In short, the app provides a user with the tools to find and create house-parties and/or other types of parties.
// To use this database, you must have a Postgresql server running on your machine, aswell as the pgAdmin 4 tool to manage the database.
// A suitable database needs to be set up in pgAdmin 4, with the name being "DatabaseTestDatabase" and the password being "DatabaseTestPassword".
// This project was created using the IntelliJ IDEA IDE in June 2022, and the Postgresql JDBC driver version 42.5.1. Newer versions of the driver may or may not work,
// so it is recommended to use the version mentioned above.
// Disclaimer: Not all of what is seen in the showcase video matches the structure of the database. The database is merely supposed to be based on the app.
public class DatabaseTest
{
    private static Connection connection;

    public static void main(String[] args)
    {
        // Uses JDBC driver version 42.5.1
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.out.println("Exception: " + e.getMessage()); }

        try {
            connection = DriverManager.getConnection
                    ("jdbc:postgresql:DatabaseTestDatabase", "postgres", "DatabaseTestPassword");

            structureDatabase();
            truncateTables();
            insertData();
            queryDatabase();

            connection.close();
        } catch (SQLException e) {
            System.out.println("Exception: " + e.getMessage());
        }
    }

    private static void structureDatabase()
    {
        try {
            Statement statement = connection.createStatement();

            statement.executeUpdate("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
            statement.executeUpdate("CREATE EXTENSION IF NOT EXISTS pgcrypto");
            // Drops table if it already exists, together with all corresponding records
            // in any child tables the table has a relationship to,
            // so that orphan data is prevented and the database remains consistent.
            // Used to test database with clean data everytime main is executed.
            statement.executeUpdate
                    ("DROP TABLE IF EXISTS user_info CASCADE");

            // Creates the user_info table
            // "email" to identify and reference each user, is primary key, as each email-address registered is unique.
            // 255 charcater VARCHAR because that is maximum length of email addresses and longer text is not needed, can't be null.
            // "first_name" limited to 30 characters as per in the Evenue app registration process, can't be null.
            // "last_name" limited to 30 characters as per in the Evenue app registration process, can't be null.
            // "age" of user. SMALLINT (-32768 to +32767) as this uses 2 bytes instead of 4, like INTEGER does.
            // Is checked for if user is already 18 years of age or older as per in the Evenue app registration process, can't be null.
            // "country" of user as two letter country codes as per ISO 3166-1 alpha-2, can't be null.

            // ### Table user_info: [email | first_name | last_name | age | country] ###
            statement.executeUpdate
                    ("""
                            CREATE TABLE user_info
                            (email VARCHAR(255) NOT NULL PRIMARY KEY,
                            first_name VARCHAR(30) NOT NULL,
                            last_name VARCHAR(30) NOT NULL,
                            age SMALLINT NOT NULL CHECK(age >= 18),
                            country char(2) NOT NULL)
                         """);

            statement.executeUpdate
                    ("DROP TABLE IF EXISTS user_login CASCADE");

            // Creates the user_login table
            // "email" references the email from user_info, so that each user activity has a corresponding user_login, can't be null.
            // "password" of the corresponding account. Inserted as TEXT and then encrypted using pgcrypto extension.
            // password is encrypted using a randomly generated sort as per the blowfish algorithm. On login the password that the user inputs
            // is encrypted and checked for equivalence to the stored encrypted password. If equivalent, the password is correct.
            // checked for minimum length of 8 characters as per in the Evenue app registration process, can't be null.

            // ### Table user_login: [email (ref.) | password] ###
            statement.executeUpdate
                    ("""
                        CREATE TABLE user_login (
                        email VARCHAR(255) NOT NULL REFERENCES user_info(email),
                        password TEXT NOT NULL CHECK (LENGTH(password) >= 8))
                        """);

            statement.executeUpdate
                    ("DROP TABLE IF EXISTS user_activity CASCADE");

            // Creates the user_activity table.
            // "email" references the email from user_info, so that each user activity has a corresponding user, can't be null.
            // "parties_created" UUID array to store the ID's of parties that a user has created, can't be null.
            // "parties_joined" UUID array to store the ID's of parties that a user has attended, can't be null.

            // ### Table user_activity: [email (ref.) | parties_hosted[] | parties_attended[]] ###
            statement.executeUpdate
                    ("""
                            CREATE TABLE user_activity
                            (email VARCHAR(255) NOT NULL REFERENCES user_info(email),
                            parties_hosted UUID[],
                            parties_attended UUID[])
                            """);
            statement.executeUpdate
                    ("DROP TABLE IF EXISTS user_reports CASCADE");

            // Creates the user_reports table
            // "user_report_id" to identify each unique user report by a uniquely generated UUID, can't be null.
            // The UUI is generated using the uuid_generate_v4() method from the uuid-ossp extension. -> https://www.postgresql.org/docs/current/uuid-ossp.html
            // "user_reporter_email" to identify the user who made the user report, can't be null.
            // "user_reported_email" to identify the user who was reported, can't be null.
            // "user_report_time" to store the date, time and timezone of when the user report was made. Can't be null.
            // "user_report_reason" - 500 Character user report reason text as per in the app, so that each report can be explained in detail, can't be null.

            // ### Table user_reports: [user_report_id | user_reporter_email (ref.) | user_reported_email (ref.) | user_report_time | user_report_reason] ###
            statement.executeUpdate
                    ("""
                            CREATE TABLE user_reports
                            (user_report_id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
                            user_reporter_email VARCHAR(255) NOT NULL REFERENCES user_info(email),
                            user_reported_email VARCHAR(255) NOT NULL REFERENCES user_info(email),
                            user_report_time TIMESTAMPTZ NOT NULL,
                            user_report_reason VARCHAR(500) NOT NULL)
                            """);

            statement.executeUpdate("DROP TYPE IF EXISTS PARTY_TYPE CASCADE");

            // Creates a "PARTY_TYPE" Enum that holds all of the different types of parties available in the app.
            statement.executeUpdate("CREATE TYPE PARTY_TYPE AS ENUM ('HAUSPARTY', 'GARTENPARTY', 'MOTTOPARTY', 'GRILLPARTY', 'RAVE', 'CLUB', 'ROOFTOPPARTY')");

            statement.executeUpdate("DROP TABLE IF EXISTS party_info CASCADE");

            // Creates the user_activity table.
            // "party_id" to identify each unique party by a uniquely generated UUID, can't be null.
            // "title" of the party. Limited to 80 Characters as per the Evenue App. Can't be null.
            // "type" of the party (See PARTY_TYPE Enum above). Can't be null.
            // "party_description". A description and further details about the party. Limited to 300 Characters and expects at
            // least 100 Characters as per the Evenue App. Can't be null.
            // "guest_description" that tells users to what kind of person the party caters to. Limited to 100 Characters
            // and expects at least 20 Characters as per the Evenue App. Cant be null.
            // "max_guests". Maximum amount of attendees. Has to be greater than or equal to 1 and less than or equal to 1000. Can't be null.
            // "host" references the user that hosts the party by his email. The host is deleted if the users account is deleted. Can't be null.
            // "attendance_fee" stores the price for attending the party up to 5 digits with 2 decimal places (drinks, food). Defaults to 0.

            // ### Table party_info: [party_id | title | type | party_description | guest_description | max_guests | host (ref.) | attendance_fee] ###
            statement.executeUpdate
                    ("""
                            CREATE TABLE party_info
                            (party_id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
                            title VARCHAR(80) NOT NULL,
                            type PARTY_TYPE NOT NULL,
                            party_description VARCHAR(300) NOT NULL CHECK (LENGTH(party_description) >= 50),
                            guest_description VARCHAR(100) NOT NULL CHECK (LENGTH(guest_description) >= 20),
                            max_guests SMALLINT NOT NULL CHECK (max_guests >= 1 AND max_guests <= 1000),
                            host VARCHAR(255) NOT NULL REFERENCES user_info(email) ON DELETE CASCADE,
                            attendance_fee NUMERIC(7, 2) DEFAULT 0)
                            """);

            statement.executeUpdate
                    ("DROP TABLE IF EXISTS favourites CASCADE");

            // Creates the favourites table.
            // "email" references a specific user.
            // "party_id" references a specific party.
            // The favourites table can be used to find out about user preferences based on favourited parties and track the amount of favourites a party receives/has.

            // ### Table favourites: [email (ref.) | party_id (ref.)] ###
            statement.executeUpdate
                    ("""
                            CREATE TABLE favourites
                            (email VARCHAR(255) NOT NULL REFERENCES user_info(email),
                            party_id UUID NOT NULL REFERENCES party_info(party_id) ON DELETE CASCADE)
                            """);

            statement.executeUpdate
                    ("DROP TABLE IF EXISTS party_datetime CASCADE");

            // Creates the party_datetime table.
            // "start-date" stored as DATE Datatype in order to store the start date of the party. Is checked for being in the future. Can't be null.
            // "start_time_tz" stored as TIMETZ  Datatype in order to store the time the party starts (with timezone). Is checked for being in the future. Can't be null.
            // "start-date" stored as DATE Datatype in order to store the end date of the party. Is checked for being in the future. Can't be null.
            // "end_time_tz" stored as TIMETZ  Datatype in order to store the time the party is over (with timezone). Is checked for being in the future. Can't be null.
            // Date and timetz stored separately instead of using TIMESTAMPTZ as it might make sense to query only the DATE of a party when for example suggesting parties
            // happening on a certain day. Splitting the time components like this also makes the table more clear to understand, especially because the date and time inputs are separate
            // in the Evenue apps party creation prompt.
            // TIMESTAMPTZ is used for reports later in the database as it makes more sense to use it there, because the time of reports
            // isn't queried very often in a real-world scenario and if it is, not only the date or time will be queried but both in order to sort which report should be reviewed next for example.

            // ### Table party_datetime: [party_id (ref.) | start_date | start_time_tz | end_date | end_time_tz] ###
            statement.executeUpdate
                    ("""
                            CREATE TABLE party_datetime
                            (party_id UUID NOT NULL REFERENCES party_info(party_id) ON DELETE CASCADE,
                            start_date DATE NOT NULL CHECK (start_date >= CURRENT_DATE),
                            start_time_tz TIMETZ NOT NULL CHECK ((start_date = CURRENT_DATE AND start_time_tz >= CURRENT_TIME) OR (start_date > CURRENT_DATE)),
                            end_date DATE NOT NULL CHECK (end_date >= start_date),
                            end_time_tz TIMETZ NOT NULL CHECK ((end_date = CURRENT_DATE AND end_time_tz >= start_time_tz) OR (end_date > start_date)))
                            """);

            statement.executeUpdate
                    ("DROP TABLE IF EXISTS party_location CASCADE");

            // Creates the party_location table.
            // "party_id" references the party_id UUID so that each party location have a corresponding party. (and vice versa)
            // "coordinates" stores the coordinates of the party (marker on map in Evenue App) as a POINT datatype (latitude, longitude), can't be null.
            // party_location table is separate from address table as spatial queries on the coordinates will have to be performed much more often than queries on the address.
            // Separating the tables can make it easier to perform these types of queries and can improve the performance of the database.

            // --- Not used because postgis has to be installed on system first: CHECK(ST_Contains(ST_MakeEnvelope(5.98865807458, 47.3024876979, 15.0169958839, 54.983104153, 4326), coordinates)))
            // Checks if the parties coordinates are within the border box confines of the camera in the Evenue app map (Restricted to only Germany), can't be null.
            // ST_Contains checks if the coordinates are within the border box defined by ST_MakeEnvelope. ST_MakeEnvelope takes 4 numeric values (latlng SW, latlng NE) and an
            // SRID (Spatial Reference ID). Here SRID '4326' is provided, which corresponds to the WGS 84 (World Geodetic System 1984) SRS,
            // which is a global standard for latitude and longitude coordinates. Needn't be stated explicitly but good practice.

            // ### Table party_location: [party_id (ref.) | coordinates] ###
            statement.executeUpdate
                    ("""
                            CREATE TABLE party_location
                            (party_id UUID NOT NULL PRIMARY KEY REFERENCES party_info(party_id) ON DELETE CASCADE,
                            coordinates POINT NOT NULL)
                            """);

            statement.executeUpdate
                    ("DROP TABLE IF EXISTS party_address CASCADE");

            // Creates the party_address table.
            // "party_id" to assign a party an address. Can't be null.
            // "street_name" stores the name of the street where the party is located, can't be null.
            // "street_number" stores the house number of where the party is located, can't be null.
            // "zip_code" stores the zip/post code of where the party is located, can't be null. Stored as VARCHAR so
            // that leading zeros are not deleted. Limited to 12 characters as the longest postcode in the world has 10 digits.
            // 2 extra to accommodate future changes.

            // ### Table party_address: [party_id (ref.) | street_name | street_number | zip_code] ###
            statement.executeUpdate
                    ("""
                            CREATE TABLE party_address
                            (party_id UUID NOT NULL PRIMARY KEY REFERENCES party_location(party_id) ON DELETE CASCADE,
                            street_name TEXT NOT NULL,
                            street_number TEXT NOT NULL,
                            zip_code VARCHAR(12) UNIQUE NOT NULL)
                            """);

            statement.executeUpdate
                    ("DROP TABLE IF EXISTS zip_code CASCADE");

            // Creates the zip code table. (Could maybe benefit from a more concise name)
            // "zip_code" references the "zip_code" of address. Zip_code table is separated because zip_code implies the city, state and country.
            // "city" stores the city where the party is located, can't be null.
            // "state" stores the state in which the party is located, can't be null.
            // "country" stores the country in which the party is located as two letter country codes as per ISO 3166-1 alpha-2, can't be null.
            // (Not redundant as the hosts user country does not necessarily have to correspond to the country the party is located in
            // if in the future the app is also accessible in other countries. example: germans host houseparty while on vacation in spain.)

            // ### Table zip_code: [zip_code (ref.) | city | state | country] ###
            statement.execute
                    ("""
                            CREATE TABLE zip_code
                            (zip_code VARCHAR(12) NOT NULL REFERENCES party_address(zip_code) ON DELETE CASCADE,
                            city TEXT NOT NULL,
                            state TEXT NOT NULL,
                            country CHAR(2) NOT NULL)
                            """);

            statement.executeUpdate
                    ("DROP TABLE IF EXISTS party_attendees CASCADE");

            statement.executeUpdate("DROP TYPE IF EXISTS ATTENDEE_STATUS CASCADE");

            // Creates an "ATTENDEE_STATUS" Enum that holds all of the different types of states a user could be in
            // in the context of attending a party.
            statement.executeUpdate("CREATE TYPE ATTENDEE_STATUS AS ENUM ('attending', 'declined', 'accepted')");

            // Creates the party_attendees table.
            // "party_id" used to identify the party for which the attendees are stored, can't be null.
            // "attendee_email" used to idetify all the party attendees. References the users email, can't be null.
            // "attendee_status" used to track the status ot the attendee.

            // ### Table party_attendees: [party_id (ref.) | attendee_email | status] ###
            statement.executeUpdate
                    ("""
                            CREATE Table party_attendees
                            (party_id UUID NOT NULL REFERENCES party_info(party_id),
                            attendee_email VARCHAR(255) NOT NULL REFERENCES user_info(email),
                            attendee_status ATTENDEE_STATUS NOT NULL)
                            """);

            statement.executeUpdate
                    ("DROP TABLE IF EXISTS party_reports CASCADE");

            // Creates the party_reports table.
            // "party_report_id" to identify each unique party report by a uniquely generated UUID, can't be null.
            // "party_reporter_email" to identify the user who made the party report, can't be null.
            // "party_id" to identify which party has been reported. Can't be null.
            // "party_report_time" to store the date, time and timezone of when the party report was made. Can't be null.
            // "party_report_reason" - 500 Character party report reason text as per in the app, so that each report can be explained in detail.

            // ### Table party_reports: [party_report_id | party_id (ref.) | party_reporter_email | party_report_time | party_report_reason] ###
            statement.executeUpdate
                    ("""
                            CREATE TABLE party_reports
                            (party_report_id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
                            party_id UUID NOT NULL REFERENCES party_info(party_id),
                            party_reporter_email VARCHAR(255) NOT NULL REFERENCES user_info(email),
                            party_report_time TIMESTAMPTZ NOT NULL,
                            party_report_reason VARCHAR(500) NOT NULL)
                            """);
            statement.close();

        } catch (SQLException e) {
            System.out.println("Exception: " + e.getMessage());
        }
    }

    private static void insertData()
    {
        try {
            //user_info
            Statement statement = connection.createStatement();
            statement.executeUpdate("INSERT INTO user_info (email, first_name, last_name, age, country) VALUES " +
                    "('lady.gaga@gmail.com', 'Lady', 'Gaga', 35, 'US'), " +
                    "('kim.kardashian@gmail.com', 'Kim', 'Kardashian', 40, 'US'), " +
                    "('kanye.west@gmail.com', 'Kanye', 'West', 44, 'US'), " +
                    "('brad.pitt@gmail.com', 'Brad', 'Pitt', 57, 'US'), " +
                    "('angelina.jolie@gmail.com', 'Angelina', 'Jolie', 45, 'US'), " +
                    "('leonardo.dicaprio@gmail.com', 'Leonardo', 'DiCaprio', 46, 'US'), " +
                    "('tom.hanks@gmail.com', 'Tom', 'Hanks', 64, 'US'), " +
                    "('meryl.streep@gmail.com', 'Meryl', 'Streep', 71, 'US'), " +
                    "('dwayne.johnson@gmail.com', 'Dwayne', 'Johnson', 48, 'US'), " +
                    "('ryan.reynolds@gmail.com', 'Ryan', 'Reynolds', 44, 'CA'), " +
                    "('michael.jackson@gmail.com', 'Michael', 'Jackson', 50, 'US'), " +
                    "('taylor.swift@gmail.com', 'Taylor', 'Swift', 31, 'US'), " +
                    "('adele@gmail.com', 'Adele', 'Adkins', 32, 'GB'), " +
                    "('ed.sheeran@gmail.com', 'Ed', 'Sheeran', 30, 'GB'), " +
                    "('justin.bieber@gmail.com', 'Justin', 'Bieber', 26, 'CA'), " +
                    "('beyonce@gmail.com', 'Beyoncé', 'Knowles', 39, 'US')");

            //user_login
            statement.executeUpdate("INSERT INTO user_login (email, password) VALUES " +
                    "('lady.gaga@gmail.com', crypt('FamousForAMeatDress126', gen_salt('bf'))), " +
                    "('kim.kardashian@gmail.com', crypt('iamkimkardashian', gen_salt('bf'))), " +
                    "('kanye.west@gmail.com', crypt('iloveMyselfMoreThanKanye', gen_salt('bf'))), " +
                    "('brad.pitt@gmail.com', crypt('BradsterBrad', gen_salt('bf'))), " +
                    "('angelina.jolie@gmail.com', crypt('TombRaiderJolie', gen_salt('bf'))), " +
                    "('leonardo.dicaprio@gmail.com', crypt('FloatingDoor', gen_salt('bf'))), " +
                    "('tom.hanks@gmail.com', crypt('Tommybro07', gen_salt('bf'))), " +
                    "('meryl.streep@gmail.com', crypt('MerylStreepPass', gen_salt('bf'))), " +
                    "('dwayne.johnson@gmail.com', crypt('TheRealRock', gen_salt('bf'))), " +
                    "('ryan.reynolds@gmail.com', crypt('Ilovetwitter', gen_salt('bf'))), " +
                    "('michael.jackson@gmail.com', crypt('Shamona198', gen_salt('bf'))), " +
                    "('taylor.swift@gmail.com', crypt('Blondehairgalxoxo', gen_salt('bf'))), " +
                    "('adele@gmail.com', crypt('RollingInTheDeep', gen_salt('bf'))), " +
                    "('ed.sheeran@gmail.com', crypt('CheekyGinger187', gen_salt('bf'))), " +
                    "('justin.bieber@gmail.com', crypt('DrewCEOJB', gen_salt('bf'))), " +
                    "('beyonce@gmail.com', crypt('JayZsWife1942', gen_salt('bf')))");

            //user_reports
            statement.executeUpdate("INSERT INTO user_reports (user_reporter_email, user_reported_email, user_report_time, user_report_reason) VALUES " +
                    "('lady.gaga@gmail.com', 'kim.kardashian@gmail.com', now(), 'Spamming'), " +
                    "('lady.gaga@gmail.com', 'kim.kardashian@gmail.com', '2022-12-18 12:34:56+02', 'Being obnoxious'), " +
                    "('adele@gmail.com', 'ed.sheeran@gmail.com', '2022-09-03 08:23:57+01', 'Trolling during karaoke'), " +
                    "('beyonce@gmail.com', 'ryan.reynolds@gmail.com', '2022-10-18 10:24:46+01', 'Cyberbullying')");

            //party_info
            statement.executeUpdate("INSERT INTO party_info (title, type, party_description, guest_description, max_guests, host, attendance_fee) VALUES " +
                    "('Celebrity Media Informatics Party!!!', 'ROOFTOPPARTY', 'Im throwing a party for celebrities on top of the tallest flatroof bulding in regensburg. A lot of fun will be had for sure. xoxo', 'A list celebrities only!!! Must be tech savvy!!!', '30', 'kim.kardashian@gmail.com', '12000.99'), " +
                    "('Musician Rave', 'RAVE', 'THE MOST TURNT UP RAVE IN GERMANY BRING ALL YOUR FRIENDS AND FRIENDS OF FRIENDS AND HAVE FUN', 'Everybody is welcome and accepted', '100', 'lady.gaga@gmail.com', '0')");

            statement.executeUpdate("INSERT INTO favourites (email, party_id)" +
                    "SELECT 'leonardo.dicaprio@gmail.com', party_id FROM party_info ORDER BY party_id LIMIT 1");

            statement.executeUpdate("INSERT INTO favourites (email, party_id)" +
                    "SELECT 'michael.jackson@gmail.com', party_id FROM party_info ORDER BY party_id LIMIT 1");

            statement.executeUpdate("INSERT INTO favourites (email, party_id)" +
                    "SELECT 'justin.bieber@gmail.com', party_id FROM party_info ORDER BY party_id LIMIT 1");

            statement.executeUpdate("INSERT INTO favourites (email, party_id)" +
                    "SELECT 'leonardo.dicaprio@gmail.com', party_id FROM party_info ORDER BY party_id LIMIT 1 OFFSET 1");

            statement.executeUpdate("INSERT INTO favourites (email, party_id)" +
                    "SELECT 'michael.jackson@gmail.com', party_id FROM party_info ORDER BY party_id LIMIT 1 OFFSET 1");

            //party_datetime
            statement.executeUpdate("INSERT INTO party_datetime (party_id, start_date, start_time_tz, end_date, end_time_tz)" +
                    "SELECT party_id, '2023-12-24', '20:00:00+02:00', '2023-12-25', '06:00:00+02:00' FROM party_info ORDER BY party_id LIMIT 1");
            statement.executeUpdate("INSERT INTO party_datetime (party_id, start_date, start_time_tz, end_date, end_time_tz)" +
                    "SELECT party_id, '2023-12-28', '18:00:00+01:00', '2023-12-29', '02:00:00+01:00' FROM party_info ORDER BY party_id LIMIT 1 OFFSET 1");

            //party_location
            statement.executeUpdate("INSERT INTO party_location (party_id, coordinates)" +
                    "SELECT party_id, '(12.357954, 51.340177)' FROM party_info ORDER BY party_id LIMIT 1");
            statement.executeUpdate("INSERT INTO party_location (party_id, coordinates)" +
                    "SELECT party_id, '(6.840363, 51.232688)' FROM party_info ORDER BY party_id LIMIT 1 OFFSET 1");

            //party_address
            statement.executeUpdate("INSERT INTO party_address (party_id, street_name, street_number, zip_code)" +
                    "SELECT party_id, 'Agnesstraße', '17', '40489' FROM party_info ORDER BY party_id LIMIT 1");
            statement.executeUpdate("INSERT INTO party_address (party_id, street_name, street_number, zip_code)" +
                    "SELECT party_id, 'Adlershelmstraße', '20', '04318' FROM party_info ORDER BY party_id LIMIT 1 OFFSET 1");

            //zip_code
            statement.executeUpdate("INSERT INTO zip_code (zip_code, city, state, country)" +
                    "SELECT zip_code, 'Leipzig', 'Saxony', 'de' FROM party_address ORDER BY zip_code LIMIT 1");
            statement.executeUpdate("INSERT INTO zip_code (zip_code, city, state, country)" +
                    "SELECT zip_code, 'Düsseldorf', 'North Rhine-Westphalia', 'de' FROM party_address ORDER BY zip_code LIMIT 1 OFFSET 1");

            //party_attendess
            statement.executeUpdate("INSERT INTO party_attendees (party_id, attendee_email, attendee_status)" +
                    "SELECT party_id, 'dwayne.johnson@gmail.com', 'accepted' FROM party_info ORDER BY party_id LIMIT 1");
            statement.executeUpdate("INSERT INTO party_attendees (party_id, attendee_email, attendee_status)" +
                    "SELECT party_id, 'dwayne.johnson@gmail.com', 'accepted' FROM party_info ORDER BY party_id LIMIT 1 OFFSET 1");
            statement.executeUpdate("INSERT INTO party_attendees (party_id, attendee_email, attendee_status)" +
                    "SELECT party_id, 'kanye.west@gmail.com', 'declined' FROM party_info ORDER BY party_id LIMIT 1 OFFSET 1");
            statement.executeUpdate("INSERT INTO party_attendees (party_id, attendee_email, attendee_status)" +
                    "SELECT party_id, 'angelina.jolie@gmail.com', 'declined' FROM party_info ORDER BY party_id LIMIT 1");
            statement.executeUpdate("INSERT INTO party_attendees (party_id, attendee_email, attendee_status)" +
                    "SELECT party_id, 'adele@gmail.com', 'declined' FROM party_info ORDER BY party_id LIMIT 1");
            statement.executeUpdate("INSERT INTO party_attendees (party_id, attendee_email, attendee_status)" +
                    "SELECT party_id, 'ed.sheeran@gmail.com', 'accepted' FROM party_info ORDER BY party_id LIMIT 1");
            statement.executeUpdate("INSERT INTO party_attendees (party_id, attendee_email, attendee_status)" +
                    "SELECT party_id, 'justin.bieber@gmail.com', 'accepted' FROM party_info ORDER BY party_id LIMIT 1");
            statement.executeUpdate("INSERT INTO party_attendees (party_id, attendee_email, attendee_status)" +
                    "SELECT party_id, 'tom.hanks@gmail.com', 'declined' FROM party_info ORDER BY party_id LIMIT 1");
            statement.executeUpdate("INSERT INTO party_attendees (party_id, attendee_email, attendee_status)" +
                    "SELECT party_id, 'tom.hanks@gmail.com', 'declined' FROM party_info ORDER BY party_id LIMIT 1 OFFSET 1");

            //party_reports
            statement.executeUpdate("INSERT INTO party_reports (party_id, party_reporter_email, party_report_time, party_report_reason)" +
                    "SELECT party_id, 'dwayne.johnson@gmail.com', '2022-12-20 11:34:36+01', 'Too few rocks lol.' FROM party_info ORDER BY party_id LIMIT 1");
            statement.executeUpdate("INSERT INTO party_reports (party_id, party_reporter_email, party_report_time, party_report_reason)" +
                    "SELECT party_id, 'dwayne.johnson@gmail.com', '2022-12-20 11:35:54+01', 'Apparently people named Dwayne are not welcome which is as discriminatory as it gets' FROM party_info ORDER BY party_id LIMIT 1");
            statement.executeUpdate("INSERT INTO party_reports (party_id, party_reporter_email, party_report_time, party_report_reason)" +
                    "SELECT party_id, 'justin.bieber@gmail.com', '2022-12-22 06:28:42+02', 'Way too many groupies allowed to attend.' FROM party_info ORDER BY party_id LIMIT 1");
            statement.executeUpdate("INSERT INTO party_reports (party_id, party_reporter_email, party_report_time, party_report_reason)" +
                    "SELECT party_id, 'kanye.west@gmail.com', '2022-11-07 21:15:13+05', 'The host cursed at me for no reason and then kicked me out.' FROM party_info ORDER BY party_id LIMIT 1 OFFSET 1");
            statement.executeUpdate("INSERT INTO party_reports (party_id, party_reporter_email, party_report_time, party_report_reason)" +
                    "SELECT party_id, 'beyonce@gmail.com', '2022-11-04 15:15:04+01', 'Highly offensive music was being played!.' FROM party_info ORDER BY party_id LIMIT 1 OFFSET 1");

            //favourites
            statement.executeUpdate("INSERT INTO favourites (party_id, email)" +
                    "SELECT party_id, 'dwayne.johnson@gmail.com' FROM party_info ORDER BY party_id LIMIT 1");
            statement.executeUpdate("INSERT INTO favourites (party_id, email)" +
                    "SELECT party_id, 'dwayne.johnson@gmail.com' FROM party_info ORDER BY party_id LIMIT 1 OFFSET 1");
            statement.executeUpdate("INSERT INTO favourites (party_id, email)" +
                    "SELECT party_id, 'ed.sheeran@gmail.com' FROM party_info ORDER BY party_id LIMIT 1");
            statement.executeUpdate("INSERT INTO favourites (party_id, email)" +
                    "SELECT party_id, 'leonardo.dicaprio@gmail.com' FROM party_info ORDER BY party_id LIMIT 1 OFFSET 1");
            statement.executeUpdate("INSERT INTO favourites (party_id, email)" +
                    "SELECT party_id, 'kanye.west@gmail.com' FROM party_info ORDER BY party_id LIMIT 1");
            statement.executeUpdate("INSERT INTO favourites (party_id, email)" +
                    "SELECT party_id, 'beyonce@gmail.com' FROM party_info ORDER BY party_id LIMIT 1");
            statement.executeUpdate("INSERT INTO favourites (party_id, email)" +
                    "SELECT party_id, 'angelina.jolie@gmail.com' FROM party_info ORDER BY party_id LIMIT 1");

            //user_activity
            /*statement.executeUpdate("INSERT INTO user_activity (parties_attended, email, parties_hosted)" +
                    "SELECT ARRAY[party_id], 'angelina.jolie@gmail.com', '' FROM party_info ORDER BY party_id LIMIT 1");*/

            statement.close();

        } catch (SQLException e) {
            System.out.println("Exception: " + e.getMessage());
        }
    }

    private static void queryDatabase() {
        try {
            Statement statement = connection.createStatement();
            ResultSet rs;

            //1 -> Counts all the parties that are in a certain country.
            rs = statement.executeQuery("SELECT COUNT(*) FROM zip_code WHERE country= 'de'");
            System.out.println("Query 1: \n" + resultToString(rs) + "\n\n");

            //2 -> Selects all of the info about the party hosts.
            rs = statement.executeQuery("SELECT email, first_name, last_name, age FROM user_info WHERE email IN (SELECT host FROM party_info)");
            System.out.println("Query 2: \n" + resultToString(rs) + "\n\n");

            //3 -> Selects all of the parties and their start and end datetimes whose start_dates are between two specified dates.
            rs = statement.executeQuery("SELECT * FROM party_datetime WHERE party_datetime.start_date BETWEEN '2023-11-28' AND '2023-12-25'");
            System.out.println("Query 3: \n" + resultToString(rs) + "\n\n");

            //4 -> Selects the average age of all of the Evenue users.
            rs = statement.executeQuery("SELECT AVG(age) FROM user_info");
            System.out.println("Query 4: \n" + resultToString(rs) + "\n\n");

            //5 -> Selects all of the distinct countries users are from.
            rs = statement.executeQuery("SELECT DISTINCT country FROM user_info");
            System.out.println("Query 5: \n" + resultToString(rs) + "\n\n");

            //6 -> Selects and joins all the parties a certain user is attending.
            rs = statement.executeQuery("SELECT * FROM party_info INNER JOIN party_attendees ON party_info.party_id = party_attendees.party_id " +
                    "WHERE party_attendees.attendee_email = 'dwayne.johnson@gmail.com'");
            System.out.println("Query 6: \n" + resultToString(rs) + "\n\n");

            //7 -> Selects an joins the full names of the attendees of a specific party.
            rs = statement.executeQuery("SELECT user_info.first_name, user_info.last_name, user_info.email " +
                    "FROM user_info INNER JOIN party_attendees ON user_info.email = party_attendees.attendee_email " +
                    "WHERE party_attendees.party_id = 'fb0c5eae-2f1b-4be8-af9d-ab09a6606c59'");
            System.out.println("Query 7: \n" + resultToString(rs) + "\n\n");

            //8 -> Selects all parties with at least one attendee and lists its id and title next to the amount of attendees in a descending order. Z
            rs = statement.executeQuery("SELECT p.party_id, p.title, COUNT(pa.attendee_email) " +
                    "AS attendees FROM party_info p JOIN party_attendees pa ON p.party_id = pa.party_id GROUP " +
                    "BY p.party_id, p.title ORDER BY attendees DESC");
            System.out.println("Query 8: \n" + resultToString(rs) + "\n\n");

            //9 -> Selects all users who are attending a party within a specified postcode.
            rs = statement.executeQuery("SELECT ui.email, ui.first_name, ui.last_name FROM user_info ui JOIN party_attendees pa ON ui.email = pa.attendee_email " +
                    "JOIN party_address pl ON pa.party_id = pl.party_id JOIN zip_code zc ON pl.zip_code = zc.zip_code " +
                    "WHERE zc.zip_code = '40489' GROUP BY ui.email, ui.first_name, ui.last_name");
            System.out.println("Query 9: \n" + resultToString(rs) + "\n\n");

            //10 -> Selects all users and the amount of parties they have hosted.
            rs = statement.executeQuery("SELECT u.email, COUNT(p.party_id) AS \"Number of Parties Hosted\" FROM user_info u " +
                    "JOIN party_info ph ON u.email = ph.host JOIN party_info p ON p.party_id = ph.party_id GROUP BY u.email");
            System.out.println("Query 10: \n" + resultToString(rs) + "\n\n");

            // Output:
            /*
            Query 1:
            2


            Query 2:
            lady.gaga@gmail.com	Lady	Gaga	35
            kim.kardashian@gmail.com	Kim	Kardashian	40


            Query 3:
            40bf8891-bdfc-4dec-9821-c2fc016ef203	2023-12-24	19:00:00	2023-12-25	05:00:00


            Query 4:
            43.8750000000000000


            Query 5:
            US
            CA
            GB


            Query 6:
            40bf8891-bdfc-4dec-9821-c2fc016ef203	Musician Rave	RAVE	THE MOST TURNT UP RAVE IN GERMANY BRING ALL YOUR FRIENDS AND FRIENDS OF FRIENDS AND HAVE FUN	Everybody is welcome and accepted	100	lady.gaga@gmail.com	0.00	40bf8891-bdfc-4dec-9821-c2fc016ef203	dwayne.johnson@gmail.com	accepted
            658c0f3a-184f-4efb-a8f2-4524d60f3611	Celebrity Media Informatics Party!!!	ROOFTOPPARTY	Im throwing a party for celebrities on top of the tallest flatroof bulding in regensburg. A lot of fun will be had for sure. xoxo	A list celebrities only!!! Must be tech savvy!!!	30	kim.kardashian@gmail.com	12000.99	658c0f3a-184f-4efb-a8f2-4524d60f3611	dwayne.johnson@gmail.com	accepted


            Query 7:



            Query 8:
            40bf8891-bdfc-4dec-9821-c2fc016ef203	Musician Rave	6
            658c0f3a-184f-4efb-a8f2-4524d60f3611	Celebrity Media Informatics Party!!!	3


            Query 9:
            adele@gmail.com	Adele	Adkins
            angelina.jolie@gmail.com	Angelina	Jolie
            dwayne.johnson@gmail.com	Dwayne	Johnson
            ed.sheeran@gmail.com	Ed	Sheeran
            justin.bieber@gmail.com	Justin	Bieber
            tom.hanks@gmail.com	Tom	Hanks


            Query 10:
            kim.kardashian@gmail.com	1
            lady.gaga@gmail.com	1
             */
        } catch (SQLException e) {
            System.out.println("Exception: " + e.getMessage());
        }
    }

    private static void truncateTables()
    {
        try {
            // Retrieves the table names and stores them in an ArrayList
            ResultSet rs = connection.getMetaData().getTables(null, null, null, new String[]{"TABLE"});
            List<String> tableNames = new ArrayList<>();
            while (rs.next()) {
                tableNames.add(rs.getString("TABLE_NAME"));
            }

            // Loops through the table names in the ArrayList and truncates them
            Statement statement = connection.createStatement();
            for (String tableName : tableNames) {
                // System.out.println(tableName);
                statement.executeUpdate("TRUNCATE TABLE " + tableName + " CASCADE");
            }
        } catch (SQLException e) {
            System.out.println("Exception: " + e.getMessage());
        }
    }

    // Helper methods for result-output
    private static List<String> resultToList(ResultSet rs)
    {
        List<String> sl = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        try
        {
            while (rs.next())
            {
                sb.setLength(0);
                for (int I = 1; true; I++)
                    try
                    {
                        sb.append(rs.getObject(I)).append('\t');
                    }
                    catch (Exception ignored)
                    {
                        sb.deleteCharAt(sb.length() - 1);
                        break;
                    }
                sl.add(sb.toString());
            }
        }
        catch (SQLException E)
        {
            System.out.println("Exception: " + E.getMessage());
        }
        return sl;
    }
    private static String resultToString(ResultSet rs)
    {
        List<String> rl = resultToList(rs);
        return String.join(System.lineSeparator(), rl);
    }
}
