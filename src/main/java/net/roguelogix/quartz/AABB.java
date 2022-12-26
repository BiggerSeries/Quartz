package net.roguelogix.quartz;

public record AABB(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public boolean contains(AABB other) {
        return other.minX > minX && other.minY > minY && other.minZ > minZ && other.maxX < maxX && other.maxY < maxY && other.maxZ < maxZ;
    }
    
    public AABB translate(int x, int y, int z) {
        return new AABB(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
    }
}
