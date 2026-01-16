#!/usr/bin/env python3
"""
Detailed Flowing Fluids Performance Analysis
Analyzes spark profile to identify uncontrolled bottlenecks
"""

import sys
import re
from collections import defaultdict, Counter

def analyze_profile(file_path):
    print("=" * 80)
    print("FLOWING FLUIDS DETAILED PERFORMANCE ANALYSIS")
    print("=" * 80)
    print(f"Analyzing: {file_path}")
    
    try:
        with open(file_path, 'rb') as f:
            content = f.read()
        
        # Convert to string for analysis
        content_str = content.decode('utf-8', errors='ignore')
        
        print(f"File size: {len(content):,} bytes")
        print()
        
        # 1. FLOWING FLUIDS SPECIFIC METHODS
        print("1. FLOWING FLUIDS METHOD ANALYSIS")
        print("-" * 50)
        
        flowing_methods = re.findall(r'traben\.flowing_fluids\.[^"\s]+', content_str)
        method_counts = Counter(flowing_methods)
        
        print(f"Total Flowing Fluids method calls: {len(flowing_methods)}")
        for method, count in method_counts.most_common(20):
            print(f"  {count:4d}: {method}")
        print()
        
        # 2. ENTITY OPERATIONS
        print("2. ENTITY OPERATIONS ANALYSIS")
        print("-" * 50)
        
        entity_patterns = [
            r'entity.*\(\d+\)',
            r'Entity.*\(\d+\)',
            r'entity.*:\s*\d+',
            r'Entity.*:\s*\d+'
        ]
        
        entity_ops = []
        for pattern in entity_patterns:
            matches = re.findall(pattern, content_str, re.IGNORECASE)
            entity_ops.extend(matches)
        
        print(f"Total entity operations found: {len(entity_ops)}")
        
        # Extract numbers from entity operations
        entity_counts = []
        for op in entity_ops:
            numbers = re.findall(r'\d+', op)
            entity_counts.extend([int(n) for n in numbers if int(n) > 0])
        
        if entity_counts:
            print(f"Entity operation counts (top 20):")
            entity_counts.sort(reverse=True)
            for count in entity_counts[:20]:
                print(f"  {count:,} operations")
        print()
        
        # 3. BLOCK OPERATIONS
        print("3. BLOCK OPERATIONS ANALYSIS")
        print("-" * 50)
        
        block_patterns = [
            r'block.*\(\d+\)',
            r'Block.*\(\d+\)',
            r'block.*:\s*\d+',
            r'Block.*:\s*\d+',
            r'LevelChunk.*\(\d+\)',
            r'PalettedContainer.*\(\d+\)'
        ]
        
        block_ops = []
        for pattern in block_patterns:
            matches = re.findall(pattern, content_str, re.IGNORECASE)
            block_ops.extend(matches)
        
        print(f"Total block operations found: {len(block_ops)}")
        
        # Extract numbers from block operations
        block_counts = []
        for op in block_ops:
            numbers = re.findall(r'\d+', op)
            block_counts.extend([int(n) for n in numbers if int(n) > 0])
        
        if block_counts:
            print(f"Block operation counts (top 20):")
            block_counts.sort(reverse=True)
            for count in block_counts[:20]:
                print(f"  {count:,} operations")
        print()
        
        # 4. THREADING AND LOCKING ISSUES
        print("4. THREADING AND LOCKING ANALYSIS")
        print("-" * 50)
        
        lock_patterns = [
            r'lock.*\(\d+\)',
            r'Lock.*\(\d+\)',
            r'sync.*\(\d+\)',
            r'Sync.*\(\d+\)',
            r'concurrent.*\(\d+\)',
            r'Concurrent.*\(\d+\)'
        ]
        
        lock_ops = []
        for pattern in lock_patterns:
            matches = re.findall(pattern, content_str, re.IGNORECASE)
            lock_ops.extend(matches)
        
        print(f"Total locking operations found: {len(lock_ops)}")
        
        # Extract numbers from lock operations
        lock_counts = []
        for op in lock_ops:
            numbers = re.findall(r'\d+', op)
            lock_counts.extend([int(n) for n in numbers if int(n) > 0])
        
        if lock_counts:
            print(f"Lock operation counts (top 20):")
            lock_counts.sort(reverse=True)
            for count in lock_counts[:20]:
                print(f"  {count:,} operations")
        print()
        
        # 5. MEMORY ALLOCATIONS
        print("5. MEMORY ALLOCATION ANALYSIS")
        print("-" * 50)
        
        allocation_patterns = [
            r'array.*\(\d+\)',
            r'Array.*\(\d+\)',
            r'list.*\(\d+\)',
            r'List.*\(\d+\)',
            r'map.*\(\d+\)',
            r'Map.*\(\d+\)',
            r'collection.*\(\d+\)',
            r'Collection.*\(\d+\)'
        ]
        
        alloc_ops = []
        for pattern in allocation_patterns:
            matches = re.findall(pattern, content_str, re.IGNORECASE)
            alloc_ops.extend(matches)
        
        print(f"Total allocation operations found: {len(alloc_ops)}")
        
        # Extract numbers from allocation operations
        alloc_counts = []
        for op in alloc_ops:
            numbers = re.findall(r'\d+', op)
            alloc_counts.extend([int(n) for n in numbers if int(n) > 0])
        
        if alloc_counts:
            print(f"Allocation operation counts (top 20):")
            alloc_counts.sort(reverse=True)
            for count in alloc_counts[:20]:
                print(f"  {count:,} operations")
        print()
        
        # 6. SPECIFIC UNCONTROLLED OPERATIONS
        print("6. UNCONTROLLED OPERATIONS IDENTIFICATION")
        print("-" * 50)
        
        # Look for specific patterns that indicate uncontrolled operations
        uncontrolled_patterns = [
            (r'FluidHeightAndDoFluidPushing.*\(\d+\)', "Fluid height/pushing operations"),
            (r'WaterStateAndDoWaterCurrentPushing.*\(\d+\)', "Water current pushing operations"),
            (r'FFFluidUtils.*\(\d+\)', "Flowing Fluids utility operations"),
            (r'canFluidFlowFromPos.*\(\d+\)', "Fluid flow checking operations"),
            (r'removeAllFluidAtPos.*\(\d+\)', "Fluid removal operations"),
            (r'setOrRemoveWaterAmountAt.*\(\d+\)', "Water amount operations"),
            (r'getCardinalsShuffle.*\(\d+\)', "Cardinal direction operations"),
            (r'placeConnectedFluids.*\(\d+\)', "Connected fluid placement operations")
        ]
        
        for pattern, description in uncontrolled_patterns:
            matches = re.findall(pattern, content_str, re.IGNORECASE)
            if matches:
                print(f"{description}: {len(matches)} occurrences")
                for match in matches[:5]:  # Show first 5
                    print(f"  {match}")
                if len(matches) > 5:
                    print(f"  ... and {len(matches) - 5} more")
                print()
        
        # 7. RECOMMENDATIONS
        print("7. OPTIMIZATION RECOMMENDATIONS")
        print("-" * 50)
        
        if entity_counts and max(entity_counts) > 1000:
            print("⚠️  HIGH ENTITY OPERATIONS DETECTED")
            print("   - Implement entity-fluid interaction throttling")
            print("   - Add distance-based entity processing")
            print("   - Consider entity batching during high MSPT")
            print()
        
        if block_counts and max(block_counts) > 1000:
            print("⚠️  HIGH BLOCK OPERATIONS DETECTED")
            print("   - Enhance block state caching")
            print("   - Implement LevelChunk operation batching")
            print("   - Add PalettedContainer access optimization")
            print()
        
        if lock_counts and max(lock_counts) > 1000:
            print("⚠️  HIGH LOCKING OPERATIONS DETECTED")
            print("   - Replace synchronized blocks with lock-free alternatives")
            print("   - Use ConcurrentHashMap for shared data structures")
            print("   - Implement atomic operations where possible")
            print()
        
        if alloc_counts and max(alloc_counts) > 1000:
            print("⚠️  HIGH MEMORY ALLOCATIONS DETECTED")
            print("   - Implement object pooling for frequently allocated objects")
            print("   - Use primitive collections where possible")
            print("   - Add allocation throttling during high MSPT")
            print()
        
        print("✅ Analysis complete!")
        
    except Exception as e:
        print(f"Error analyzing file: {e}")
        return False
    
    return True

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python detailed_fluids_analysis.py <spark_profile_file>")
        sys.exit(1)
    
    file_path = sys.argv[1]
    analyze_profile(file_path)
