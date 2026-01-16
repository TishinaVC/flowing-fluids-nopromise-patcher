#!/usr/bin/env python3
import struct
import sys
from collections import defaultdict

def read_varint(data, offset):
    """Read a varint from protobuf data"""
    result = 0
    shift = 0
    pos = offset
    while True:
        if pos >= len(data):
            return None, pos
        byte = data[pos]
        pos += 1
        result |= (byte & 0x7F) << shift
        if not (byte & 0x80):
            break
        shift += 7
        if shift >= 64:
            return None, pos
    return result, pos

def analyze_protobuf_file(filename):
    """Analyze protobuf file for performance bottlenecks"""
    with open(filename, 'rb') as f:
        data = f.read()
    
    print(f"File size: {len(data)} bytes")
    print(f"Analyzing protobuf structure...")
    
    # Track field statistics
    field_counts = defaultdict(int)
    field_sizes = defaultdict(list)
    message_depth = []
    current_depth = 0
    total_operations = 0
    
    pos = 0
    while pos < len(data):
        if pos > len(data) - 1:
            break
            
        # Read key (field number + wire type)
        key, pos = read_varint(data, pos)
        if key is None:
            break
            
        total_operations += 1
        field_number = key >> 3
        wire_type = key & 0x7
        
        field_counts[field_number] += 1
        
        # Process based on wire type
        if wire_type == 0:  # varint
            value, pos = read_varint(data, pos)
            if value is not None:
                field_sizes[field_number].append(1)
                
        elif wire_type == 1:  # 64-bit
            pos += 8
            field_sizes[field_number].append(8)
            
        elif wire_type == 2:  # length-delimited
            length, pos = read_varint(data, pos)
            if length is not None:
                if pos + length <= len(data):
                    # Check if this might be a nested message
                    if length > 100:  # Large nested message
                        current_depth += 1
                        message_depth.append(current_depth)
                    field_sizes[field_number].append(length)
                    pos += length
                    if current_depth > 0:
                        current_depth -= 1
                        
        elif wire_type == 5:  # 32-bit
            pos += 4
            field_sizes[field_number].append(4)
            
        else:
            # Skip unknown wire types
            break
    
    # Analysis results
    print(f"\n=== PERFORMANCE ANALYSIS ===")
    print(f"Total operations: {total_operations}")
    print(f"Unique fields: {len(field_counts)}")
    print(f"Max message depth: {max(message_depth) if message_depth else 0}")
    
    print(f"\n=== TOP 10 MOST FREQUENT FIELDS ===")
    sorted_fields = sorted(field_counts.items(), key=lambda x: x[1], reverse=True)
    for field_num, count in sorted_fields[:10]:
        avg_size = sum(field_sizes[field_num]) / len(field_sizes[field_num]) if field_sizes[field_num] else 0
        total_size = sum(field_sizes[field_num])
        print(f"Field {field_num}: {count} occurrences, avg size: {avg_size:.1f}, total: {total_size} bytes")
    
    print(f"\n=== POTENTIAL BOTTLENECKS ===")
    
    # Find fields with high frequency
    high_freq_fields = [(f, c) for f, c in field_counts.items() if c > 1000]
    if high_freq_fields:
        print("Fields with high frequency (>1000 occurrences):")
        for field_num, count in sorted(high_freq_fields, key=lambda x: x[1], reverse=True)[:5]:
            print(f"  Field {field_num}: {count} times")
    
    # Find fields with large total size
    large_fields = [(f, sum(sizes)) for f, sizes in field_sizes.items() if sum(sizes) > 10000]
    if large_fields:
        print("Fields with large total size (>10KB):")
        for field_num, total_size in sorted(large_fields, key=lambda x: x[1], reverse=True)[:5]:
            count = field_counts[field_num]
            print(f"  Field {field_num}: {total_size} bytes across {count} occurrences")
    
    # Calculate potential savings from optimization
    print(f"\n=== OPTIMIZATION RECOMMENDATIONS ===")
    
    # Recommend field packing for repeated small fields
    small_repeated_fields = []
    for field_num, sizes in field_sizes.items():
        if len(sizes) > 100 and sum(sizes) / len(sizes) < 50:
            small_repeated_fields.append((field_num, len(sizes), sum(sizes)))
    
    if small_repeated_fields:
        print("Consider packing these repeated fields:")
        for field_num, count, total_size in sorted(small_repeated_fields, key=lambda x: x[2], reverse=True)[:5]:
            print(f"  Field {field_num}: {count} small items, {total_size} bytes total")

if __name__ == "__main__":
    analyze_protobuf_file("gFXutBMo7o.sparkprofile")
