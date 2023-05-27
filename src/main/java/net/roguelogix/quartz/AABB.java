package net.roguelogix.quartz;

public record AABB(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    
    public AABB() {
        this(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }
    
    public boolean contains(AABB other) {
        return other.minX > minX && other.minY > minY && other.minZ > minZ && other.maxX < maxX && other.maxY < maxY && other.maxZ < maxZ;
    }
    
    public AABB translate(int x, int y, int z) {
        return new AABB(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
    }
    
    public AABB union(AABB aabb) {
        return new AABB(Math.min(minX, aabb.minX), Math.min(minY, aabb.minY), Math.min(minZ, aabb.minZ),
                Math.max(maxX, aabb.maxX), Math.max(maxY, aabb.maxY), Math.max(maxZ, aabb.maxZ));
    }
}
