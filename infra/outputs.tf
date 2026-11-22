output "server_ip" {
  description = "서버의 탄력적 IP"
  value       = data.aws_eip.main.public_ip
}

output "ssh_command" {
  value = "ssh -i ~/.ssh/${var.key_pair_name}.pem ubuntu@${data.aws_eip.main.public_ip}"
}

output "db_endpoint" {
  description = "서버 .env의 SPRING_DATASOURCE_URL에 쓰는 RDS 주소"
  value       = aws_db_instance.main.address
}
