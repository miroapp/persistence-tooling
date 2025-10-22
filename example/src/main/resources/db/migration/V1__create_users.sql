CREATE SEQUENCE users_id_seq;

CREATE TABLE users
(
    id INT PRIMARY KEY DEFAULT nextval('users_id_seq')
);
