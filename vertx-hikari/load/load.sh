echo "POST http://localhost/items" | vegeta attack -duration=5s -bodyString={"name":"ittt"} | tee results.bin | vegeta report
