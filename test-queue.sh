#!/bin/bash

echo "🔥 JAWS Queue System Test Script 🔥"
echo "===================================="

# Function to start JAWS instance
start_jaws() {
    local mode=$1
    local port=$2
    
    echo "🚀 Starting JAWS in $mode mode on port $port..."
    
    # Start JAWS in background with environment variables
    env JAWS_MODE="$mode" PORT="$port" mvn exec:java -Dexec.mainClass=org.ruitx.Jaws > "logs-$mode.txt" 2>&1 &
    local pid=$!
    
    echo "📍 JAWS $mode started with PID $pid"
    echo $pid > "jaws-$mode.pid"
    
    # Wait for startup
    sleep 3
    
    return 0
}

# Function to stop JAWS instances
stop_all() {
    echo "🛑 Stopping all JAWS instances..."
    pkill -f "exec:java.*org.ruitx.Jaws" || true
    rm -f jaws-*.pid logs-*.txt
}

# Function to test the queue
test_queue() {
    echo "🧪 Testing queue system..."
    echo "📡 Making request to head instance (port 15000)..."
    
    response=$(curl -s http://localhost:15000/api/v1/status)
    
    if [ $? -eq 0 ]; then
        echo "✅ Response received:"
        echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"
    else
        echo "❌ Failed to get response"
    fi
}

# Main script
case "$1" in
    "start")
        echo "Starting queue system..."
        
        # Start head first (so broker is available)
        start_jaws "head" "15000"
        
        # Wait longer for head to fully initialize broker
        echo "⏳ Waiting for head to initialize broker..."
        sleep 8
        
        # Start worker
        start_jaws "worker" "15001"
        
        echo "✅ Both instances started!"
        echo "🎯 Head: http://localhost:15000"
        echo "⚡ Worker: http://localhost:15001"
        ;;
    "test")
        test_queue
        ;;
    "stop")
        stop_all
        echo "✅ All instances stopped"
        ;;
    "logs")
        echo "📋 Head logs:"
        cat logs-head.txt 2>/dev/null || echo "No head logs found"
        echo ""
        echo "📋 Worker logs:"
        cat logs-worker.txt 2>/dev/null || echo "No worker logs found"
        ;;
    *)
        echo "Usage: $0 {start|test|stop|logs}"
        echo "  start - Start head and worker instances"
        echo "  test  - Test the queue system"
        echo "  stop  - Stop all instances"
        echo "  logs  - Show logs from both instances"
        ;;
esac 