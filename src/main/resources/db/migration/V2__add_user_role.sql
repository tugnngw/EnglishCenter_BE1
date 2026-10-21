-- Migration V2: Add USER role for newly registered accounts
INSERT INTO roles (role_name, description)
VALUES ('USER', 'Registered user - has not purchased any course')
ON CONFLICT (role_name) DO NOTHING;
