

# StreamVault WebApp

StreamVault is a full-stack web application designed for managing and streaming media content. The project provides user authentication, content organization, and media management features through an interactive web interface.

## Features

- User authentication and authorization
- Media/content management
- Search and filtering functionality
- Responsive web interface
- Database integration
- Organized backend architecture

## Technologies Used

### Backend
- Java
- Spring Boot
- Maven

### Frontend
- HTML
- CSS
- JavaScript

### Database
- MySQL

## Project Structure

```text
src/
├── main/
│   ├── java/
│   ├── resources/
│   └── webapp/
└── test/
````

## Getting Started

### Prerequisites

Make sure you have the following installed:

* Java JDK 17+ (or your project version)
* Maven
* MySQL
* IntelliJ IDEA / VS Code / Eclipse

## Installation

1. Clone the repository:

```bash
git clone https://github.com/Ahmaddd-y/streamvault-webapp.git
```

2. Navigate to the project directory:

```bash
cd streamvault-webapp
```

3. Configure the database connection inside:

```text
src/main/resources/application.properties
```

4. Install dependencies and build the project:

```bash
mvn clean install
```

5. Run the application:

```bash
mvn spring-boot:run
```

6. Open the application in your browser:

```text
http://localhost:8080
```

## Future Improvements

* Video streaming optimization
* User playlists
* Recommendation system
* Admin dashboard enhancements
* Cloud deployment support

## License

This project is for educational and portfolio purposes.


