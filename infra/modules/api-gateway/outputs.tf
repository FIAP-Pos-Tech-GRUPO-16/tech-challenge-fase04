output "url_api" {
  value = aws_apigatewayv2_api.http_api.api_endpoint
}

output "id_api" {
  value = aws_apigatewayv2_api.http_api.id
}
