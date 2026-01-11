CREATE USER 'matrics_username'@'%' IDENTIFIED BY 'metrics_userpass';

GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'matrics_username'@'%';
FLUSH PRIVILEGES;