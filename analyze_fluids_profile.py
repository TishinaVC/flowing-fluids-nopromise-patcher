#!/usr/bin/env python3
import re

def analyze_flowing_fluids_profile(filename):
    """Analyze spark profile for Flowing Fluids mod performance issues"""
    with open(filename, 'rb') as f:
        data = f.read()
    
    print("FLOWING FLUIDS MOD PERFORMANCE ANALYSIS")
    print("=" * 60)
    print(f"File size: {len(data)} bytes")
    
    # Look for Flowing Fluids specific strings
    print(f"\n1. FLOWING FLUIDS MOD REFERENCES:")
    
    fluid_patterns = [
        b'flowing',
        b'fluid',
        b'traben',
        b'FlowingFluids',
        b'liquid',
        b'water',
        b'lava'
    ]
    
    fluid_references = {}
    for pattern in fluid_patterns:
        matches = re.findall(pattern + b'.{0,50}', data, re.IGNORECASE)
        if matches:
            fluid_references[pattern.decode()] = len(matches)
            print(f"  {pattern.decode()}: {len(matches)} occurrences")
            
            # Show relevant matches
            for match in matches[:5]:
                try:
                    decoded = match.decode('utf-8', errors='ignore')
                    if len(decoded) > 10:
                        print(f"    {decoded}")
                except:
                    pass
    
    print(f"\n2. PERFORMANCE BOTTLENECKS:")
    
    # Look for performance-related patterns
    perf_patterns = [
        b'tick',
        b'update',
        b'render',
        b'process',
        b'entity',
        b'chunk',
        b'block',
        b'mspt',
        b'tps'
    ]
    
    perf_data = {}
    for pattern in perf_patterns:
        matches = re.findall(pattern + b'.{0,30}', data, re.IGNORECASE)
        if matches:
            perf_data[pattern.decode()] = len(matches)
            if len(matches) > 20:  # Only show high-frequency operations
                print(f"  {pattern.decode()}: {len(matches)} occurrences")
    
    print(f"\n3. METHOD SIGNATURES (Flowing Fluids related):")
    
    # Look for method signatures that might be related to fluid operations
    method_patterns = [
        b'm_\\d+_\\d+',  # Obfuscated Minecraft methods
        b'func_\\d+',     # Obfuscated functions
        b'animateTick',
        b'randomTick',
        b'updateShape',
        b'getFlow',
        b'spread'
    ]
    
    for pattern in method_patterns:
        matches = re.findall(pattern + b'.{0,40}', data, re.IGNORECASE)
        if matches:
            fluid_related = [m for m in matches if any(fp in m.lower() for fp in [b'fluid', b'flow', b'water', b'lava'])]
            if fluid_related:
                print(f"  {pattern.decode()}: {len(fluid_related)} fluid-related")
                for match in fluid_related[:3]:
                    try:
                        decoded = match.decode('utf-8', errors='ignore')
                        if len(decoded) > 15:
                            print(f"    {decoded}")
                    except:
                        pass
    
    print(f"\n4. MEMORY ALLOCATIONS:")
    
    # Look for memory allocation patterns
    memory_patterns = [
        b'allocate',
        b'new ',
        b'array',
        b'list',
        b'map',
        b'collection',
        b'byte',
        b'buffer'
    ]
    
    for pattern in memory_patterns:
        matches = re.findall(pattern + b'.{0,25}', data, re.IGNORECASE)
        if matches and len(matches) > 10:
            print(f"  {pattern.decode()}: {len(matches)} allocations")
    
    print(f"\n5. THREADING ISSUES:")
    
    # Look for threading-related issues
    thread_patterns = [
        b'thread',
        b'lock',
        b'sync',
        b'wait',
        b'block',
        b'concurrent'
    ]
    
    for pattern in thread_patterns:
        matches = re.findall(pattern + b'.{0,30}', data, re.IGNORECASE)
        if matches:
            print(f"  {pattern.decode()}: {len(matches)} occurrences")
    
    print(f"\n6. SPECIFIC PERFORMANCE METRICS:")
    
    # Look for numeric performance data
    numeric_patterns = [
        b'\\d+\\.\\d+ms',
        b'\\d+ms',
        b'\\d+\\.\\d+%',
        b'\\d+%',
        b'TPS:\\d+',
        b'MSPT:\\d+'
    ]
    
    for pattern in numeric_patterns:
        matches = re.findall(pattern, data)
        if matches:
            print(f"  {pattern.decode()}: {len(matches)} values")
            # Show unique values
            unique_values = list(set(matches[:10]))
            for value in unique_values[:5]:
                try:
                    decoded = value.decode('utf-8', errors='ignore')
                    print(f"    {decoded}")
                except:
                    pass
    
    return fluid_references, perf_data

if __name__ == "__main__":
    analyze_flowing_fluids_profile("qVHci47tDG.sparkprofile")
