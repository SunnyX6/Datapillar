"""
Clear Neo4j database.
Delete all nodes, relationships, constraints, and indexes.
"""

import logging
import os
import sys
from pathlib import Path

from neo4j import GraphDatabase

# Configure logging.
logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)

# Add project root to Python path.
project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root))

# Read configuration directly from environment variables to avoid importing src.
NEO4J_URI = os.getenv("NEO4J_URI", "bolt://localhost:7687")
NEO4J_USERNAME = os.getenv("NEO4J_USERNAME", "neo4j")
NEO4J_PASSWORD = os.getenv("NEO4J_PASSWORD", "123456asd")


def clear_neo4j_database(driver) -> None:
    """Clear Neo4j database."""
    logger.warning("=" * 80)
    logger.warning(
        "Ready to clear Neo4j database "
        "(delete all nodes, relationships, constraints, and indexes)"
    )
    logger.warning("=" * 80)

    try:
        with driver.session() as session:
            # 1. Delete all nodes and relationships.
            logger.info("\nStep 1/3: delete all nodes and relationships...")
            session.run("MATCH (n) DETACH DELETE n")
            logger.info("All nodes and relationships deleted")

            # 2. Remove all constraints.
            logger.info("\nStep 2/3: remove all constraints...")
            constraints = [record.data() for record in session.run("SHOW CONSTRAINTS")]
            if constraints:
                for constraint in constraints:
                    constraint_name = constraint.get("name")
                    if constraint_name:
                        try:
                            session.run(f"DROP CONSTRAINT {constraint_name} IF EXISTS")
                            logger.info("Constraint removed: %s", constraint_name)
                        except BaseException as exc:
                            logger.warning(
                                "Failed to delete constraint %s: %s", constraint_name, exc
                            )
                logger.info("Deleted %s constraints", len(constraints))
            else:
                logger.info("No constraints to delete")

            # 3. Delete all indexes.
            logger.info("\nStep 3/3: delete all indexes...")
            indexes = [record.data() for record in session.run("SHOW INDEXES")]
            if indexes:
                for index in indexes:
                    index_name = index.get("name")
                    index_type = index.get("type", "")
                    # Skip indexes auto-created by constraints.
                    if index_name and "CONSTRAINT" not in index_type.upper():
                        try:
                            session.run(f"DROP INDEX {index_name} IF EXISTS")
                            logger.info("Index deleted: %s", index_name)
                        except BaseException as exc:
                            logger.warning("Failed to delete index %s: %s", index_name, exc)
                logger.info("Processed %s indexes", len(indexes))
            else:
                logger.info("No indexes to delete")

        logger.info("\n" + "=" * 80)
        logger.info("Neo4j database has been fully cleared")
        logger.info("=" * 80)
    except BaseException as exc:
        logger.error("Failed to clear database: %s", exc)
        raise


def main() -> None:
    """Entry point."""
    logger.info("Neo4j database cleanup tool")
    logger.info("Connecting to Neo4j: %s", NEO4J_URI)

    driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USERNAME, NEO4J_PASSWORD))
    try:
        driver.verify_connectivity()
        logger.info("Neo4j connection successful")
        clear_neo4j_database(driver)
    except BaseException as exc:
        logger.error("Operation failed: %s", exc)
        raise
    finally:
        driver.close()
        logger.info("Neo4j connection closed")


if __name__ == "__main__":
    main()
