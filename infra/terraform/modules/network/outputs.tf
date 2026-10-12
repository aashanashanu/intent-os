output "vpc_id" { value = aws_vpc.app.id }
output "vpc_cidr" { value = aws_vpc.app.cidr_block }
output "private_subnet_id" {
  value      = aws_subnet.private.id
  depends_on = [aws_route.private_default]
}