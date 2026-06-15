val extracted = """{\"_id\":\"123\",\"title\":\"Hello \\n World\"}"""
println("Original: " + extracted)
val fixed = extracted.replace("\\\"", "\"").replace("\\n", "\n")
println("Fixed: " + fixed)
