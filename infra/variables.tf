variable "region" {
  description = "AWS 리전 (서울만 사용)"
  type        = string
  default     = "ap-northeast-2"
}

variable "aws_profile" {
  description = "aws configure로 만든 프로필 이름"
  type        = string
  default     = "devcos-team06"
}

variable "my_ip_cidr" {
  description = "SSH(22번)를 허용할 내 공인 IP. 예: 203.0.113.10/32"
  type        = string

  validation {
    condition     = can(cidrhost(var.my_ip_cidr, 0)) && var.my_ip_cidr != "0.0.0.0/0"
    error_message = "my_ip_cidr는 올바른 CIDR이어야 하며 0.0.0.0/0은 허용하지 않습니다."
  }
}

variable "db_password" {
  description = "RDS 마스터 비밀번호. TF_VAR_db_password 환경 변수로 전달한다."
  type        = string
  sensitive   = true
}

variable "key_pair_name" {
  description = "콘솔에서 미리 만든 키 페어 이름"
  type        = string
  default     = "devcos-team06-key"
}

variable "eip_name_tag" {
  description = "콘솔에서 미리 할당한 탄력적 IP의 Name 태그 값"
  type        = string
  default     = "devcos-team06-eip"
}

variable "ec2_instance_type" {
  type    = string
  default = "t3.medium"
}

variable "db_instance_class" {
  description = "부족하면 db.t4g.small로 올린다."
  type        = string
  default     = "db.t4g.micro"
}
