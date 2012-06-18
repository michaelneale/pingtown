

(200..1200).each do |i|
         puts "DOING #{i}"
	 `curl -X "POST" -d "url=http://ec2-50-19-49-216.compute-1.amazonaws.com:8080/?test=#{i}&interval=60" http://pingtown.beescloud.com/tasks`
	 sleep 0.1
end
