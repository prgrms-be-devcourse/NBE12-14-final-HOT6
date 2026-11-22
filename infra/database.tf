resource "aws_db_subnet_group" "main" {
  name       = "${local.prefix}-db-subnets"
  subnet_ids = [aws_subnet.private_a.id, aws_subnet.private_c.id]

  tags = { Name = "${local.prefix}-db-subnets" }
}

resource "aws_db_instance" "main" {
  # 다른 리소스는 이름 규칙(devcos-team06--종류)에 따라 하이픈을 두 개 쓰지만,
  # RDS 식별자는 하이픈이 연속될 수 없어서 이 리소스만 하나만 쓴다.
  identifier     = "devcos-team06-db"
  engine         = "postgres"
  engine_version = "17"
  instance_class = var.db_instance_class

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = "devpay"
  username = "devpay"
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period = 1
  skip_final_snapshot     = true
  apply_immediately       = true

  tags = { Name = "${local.prefix}-db" }

  lifecycle {
    ignore_changes = [engine_version]
  }
}
