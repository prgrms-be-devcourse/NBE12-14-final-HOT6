data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }
}

# 콘솔에서 미리 만든 자원은 만들지 않고 이름으로 찾아서 연결만 한다. destroy해도 남는다.
data "aws_key_pair" "main" {
  key_name = var.key_pair_name
}

data "aws_eip" "main" {
  filter {
    name   = "tag:Name"
    values = [var.eip_name_tag]
  }
}

resource "aws_instance" "web" {
  ami                         = data.aws_ami.ubuntu.id
  instance_type               = var.ec2_instance_type
  subnet_id                   = aws_subnet.public.id
  vpc_security_group_ids      = [aws_security_group.web.id]
  key_name                    = data.aws_key_pair.main.key_name
  associate_public_ip_address = true
  user_data                   = file("${path.module}/user_data.sh")

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = 20
  }

  volume_tags = {
    Name = "${local.prefix}-web-root"
    Team = "devcos-team06"
  }

  tags = { Name = "${local.prefix}-web" }

  lifecycle {
    # 새 AMI가 나오거나 user_data를 고쳐도 서버가 통째로 교체되지 않게 한다.
    ignore_changes = [ami, user_data]
  }
}

resource "aws_eip_association" "web" {
  instance_id   = aws_instance.web.id
  allocation_id = data.aws_eip.main.id
}
